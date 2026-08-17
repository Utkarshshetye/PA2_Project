import java.util.*;

import soot.*;
import soot.jimple.AnyNewExpr;
import soot.jimple.Ref;
import soot.jimple.internal.JAssignStmt;
import soot.jimple.internal.JNewExpr;
import soot.toolkits.graph.*;
import soot.toolkits.scalar.BackwardFlowAnalysis;
import soot.toolkits.scalar.FlowSet;
import java.net.URISyntaxException;
import java.security.URIParameter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.TreeMap;

import soot.jimple.ArrayRef;
import soot.jimple.AssignStmt;
import soot.jimple.InstanceFieldRef;
import soot.jimple.InstanceInvokeExpr;
import soot.jimple.IntConstant;
import soot.jimple.InvokeExpr;
import soot.jimple.NewArrayExpr;
import soot.jimple.NewExpr;
import soot.jimple.Stmt;
import soot.toolkits.scalar.ForwardFlowAnalysis;

public class AnalysisTransformer extends BodyTransformer {
   

    class PointToGraph {
        // a -> Oi
        Map<Local, Set<Integer>> objMap = new HashMap<>();
        
        // field -> Oi's
        Map<String, Set<Integer>> fieldMap = new HashMap<>();
        

        Map<String, Local> loads = new HashMap<>();

        // Alias map
        Map<Local, Local> aliasMap = new HashMap<>();

        Set<Integer> passVar = new HashSet<>();

        public boolean top = false;

        public PointToGraph(boolean top) {
            this.top = top;
            this.passVar.add(0);
        }

        public PointToGraph() {
            this.top = false;
            this.passVar.add(0);
        }

        public void moveFlowFacts(PointToGraph pg2) {
            objMap = new HashMap<>();
            fieldMap = new HashMap<>();
            loads = new HashMap<>(pg2.loads);
            aliasMap = new HashMap<>(pg2.aliasMap);
            top = pg2.top;
            passVar = new HashSet<>(pg2.passVar);
            for (Map.Entry<Local, Set<Integer>> iter: pg2.objMap.entrySet()) {
                objMap.put(iter.getKey(), iter.getValue());
            }
            
            for(Map.Entry<String, Set<Integer>> iter2: pg2.fieldMap.entrySet()) {
                fieldMap.put(iter2.getKey(), iter2.getValue());
            }
        }

        @Override
        public boolean equals(Object obj) {
            if (!(obj instanceof PointToGraph)) return false;

            PointToGraph pg = (PointToGraph) obj;

            if (this.top != pg.top) return false;
            if (!this.objMap.equals(pg.objMap)) return false;
            if (!this.fieldMap.equals(pg.fieldMap)) return false;
            if (!this.loads.equals(pg.loads)) return false;
            if (!this.passVar.equals(pg.passVar)) return false;
            
            return this.aliasMap.equals(pg.aliasMap);

            // return this.top == pg.top && pg.objMap.equals(this.objMap) && pg.fieldMap.equals(this.fieldMap) && pg.loads.equals(this.loads) && pg.aliasMap.equals(this.aliasMap);
        }

        @Override
        public int hashCode() {
            // TODO Auto-generated method stub
            return Objects.hash(objMap, fieldMap, loads, aliasMap, top, passVar);
        }

        public String arrayRefKey(Local b, Value idx) {
            Local aliasValue = aliasMap.getOrDefault(b, b);
            String arrIdx = "[]";

            if (idx instanceof IntConstant) {
                IntConstant constant = (IntConstant) idx;
                arrIdx = "[" + constant.value + "]";
            } else if (idx instanceof Local) {
                Local newIdx = (Local) idx;
                Local locIdx = aliasMap.getOrDefault(newIdx, newIdx);
                arrIdx = "[" + locIdx.getName() + "]";
            }

            return aliasValue.getName() + arrIdx;
        }

        public String getKey(Local base, SootField sfi) {
            Local entry = aliasMap.getOrDefault(base, base);
            return entry.getName() + "." + sfi.getSignature();
        }
    }

    // Result
    static Map<String, List<String>> finalResult = new TreeMap<>();

    class PointToGraphAnalysis extends ForwardFlowAnalysis<Unit, PointToGraph> {

        String sootClass;
        String sootMethod;
        Body body;
        List<String> redundant;
      
        public PointToGraphAnalysis(SootMethod sMethod, String mName, Body body, String cName, UnitGraph uGraph) {
            super(uGraph);
            this.sootClass = cName;
            this.sootMethod = mName;
            this.body = uGraph.getBody();
            doAnalysis();
        }

        @Override
        protected void flowThrough(PointToGraph in, Unit unit, PointToGraph out) {

            // System.out.println("Line:"+ unit);
            // System.out.println(" In SET:"+ in.loads.keySet());
            // Copy out graph to in
            out.moveFlowFacts(in);
            
            if (unit instanceof AssignStmt) {
                // ... = ...
                AssignStmt assignStmt = (AssignStmt) unit;

                Value lop = assignStmt.getLeftOp();
                Value rop = assignStmt.getRightOp();

                if (rop instanceof NewExpr || rop instanceof NewArrayExpr) {
                    // v = new T()

                    if (lop instanceof Local) {
                        //10. A a = new A(), o10
                        Local lVar = (Local) lop;
                        int objNo = assignStmt.getJavaSourceStartLineNumber();
                        
                        Set<Integer> objsPntTo = new HashSet<>();
                        objsPntTo.add(objNo);

                        out.objMap.put(lVar, objsPntTo);
                        
                        // If already some entry store, then remove, for field sensitivity
                        // List<Local> locals = (List<Local>) out.loads.values();
                        
                        // for (Local local : locals) {
                        //     out.objMap.remove(local);
                        // }

                        invalidateAvailLoadDest(lVar, out);
                        out.aliasMap.remove(lVar);
                    }
                } 
                
                else if (lop instanceof Local) {
                    // field access, Node c = ...;
                    Local v = (Local) lop;

                    if (rop instanceof Local) {
                        // v = w
                        // copy, rhs to lhs

                        Local w = (Local) rop;

                        HashSet<Integer> pointing = (HashSet<Integer>) in.objMap.getOrDefault(w, new HashSet<>());
                        
                        out.objMap.put(v, pointing);

                        // If already some entry store, then remove, for field sensitivity
                        List<Local> locals = (List<Local>) out.loads.values();
                        
                        for (Local local : locals) {
                            out.objMap.remove(local);
                        }
                        
                        Local exist = in.aliasMap.get(w);

                        if (exist != null) {
                            out.aliasMap.put(v, exist);
                        } else {
                            out.aliasMap.put(v, w);
                        }


                        invalidateAvailLoadDest(v, out);
                        // out.aliasMap.remove(lVar);
                    }

                    else if (rop instanceof InstanceFieldRef) {
                        // v = w.f
                        InstanceFieldRef instField = (InstanceFieldRef) rop;

                        // If already entry present, redundant, replacement found
                        Value base = instField.getBase();
                        SootField second = instField.getField();
                        
                        String availKey = base + "." + second;

                        Local isPrent = in.loads.get(availKey);

                        Set<Integer> bPtr = in.objMap.getOrDefault(base, new HashSet<>());
                        Set<Integer> rPtr = new HashSet<>();
                        boolean passed = false;

                        for (int id : bPtr) {
                            String key = id + "." + second.getSignature();
                            Set<Integer> values = in.fieldMap.get(key);
                     
                            if (in.passVar.contains(id)) {
                                passed = true;
                            }

                            if (values!=null) {
                                rPtr.addAll(values);
                            }
                        }
                        
                        if (passed) {
                            rPtr.add(0);
                        }

                        out.objMap.put((Local) lop, rPtr);

                        // invalidate available loads destination
                        // out.loads.values().removeIf(t->t.equals((Local) lop));

                        Local provi = (isPrent != null) ? isPrent : (Local) lop;
                        
                        invalidateAvailLoadDest(v, out);

                        // String k2 = base + "." + second.getSignature();
                        String k2 = out.getKey((Local) base, second);

                        out.loads.put(k2, provi);

                        // System.out.println("BASE escaped: " + passed);

                        if (!passed) {
                            if (isPrent!=null) {
                                out.aliasMap.put(v, isPrent);
                            } else {
                                out.aliasMap.remove(v);
                            }

                        } else {
                            out.aliasMap.remove(v);
                        }

                        // if (isPrent != null) {
                        //     int lno = unit.getJavaSourceStartLineNumber();
                            
                        //     String op = "Line " + lno + ": " + ((Local)instField.getBase()).getName() + "." + instField.getField().getName() + " (can be replaced with " + isPrent.getName() + ")";
                            
                        //     redundant.add(op);
                        // }
                        
                        // Local base = (Local) instField.getBase();

                        // Set<Integer> ptr = in.objMap.getOrDefault(base, Collections.emptySet());

                        // Set<Integer> res = new HashSet<>();

                        // for (Integer integer : ptr) {
                        //     String key = integer + "." + instField.getField().getSignature();

                        //     res.addAll(in.fieldMap.getOrDefault(key, Collections.emptySet()));
                        // }

                        // out.objMap.put((Local) lop, res);

                        // String newKey = base.getName() + "." + instField.getField().getSignature();

                        // List<Local> locals = (List<Local>) out.loads.values();
                        
                        // for (Local local : locals) {
                        //     out.objMap.remove(local);
                        // }
                        
                        // out.loads.put(newKey, (Local) lop);
                    
                    } else if (rop instanceof ArrayRef) {
                        // x = arr[]
                        
                        ArrayRef arrRef = (ArrayRef) rop;
                        Local baseArr = (Local) arrRef.getBase();
                        Value idx = arrRef.getIndex();

                        String key = in.arrayRefKey(baseArr, idx);
                        Local prev = in.loads.get(key);

                        // String arrKey = 
                        Set<Integer> arrPointer = in.objMap.getOrDefault(baseArr, new HashSet<>());
                        Set<Integer> arrRes = new HashSet<>();
                        boolean arrPased = false;
                        
                        for (Integer id : arrPointer) {
                            String arrFieldKey = id + ".[]";

                            if (in.passVar.contains(id)) {
                                arrPased = true;
                            }

                            Set<Integer> contents = in.fieldMap.get(arrFieldKey);

                            if(!contents.isEmpty()) {
                                arrRes.addAll(contents);
                            }
                        }

                        if (arrPased) {
                            arrRes.add(0);
                        }

                        out.objMap.put(v, arrRes);

                        invalidateArrayLoads(v, out);

                        Local previous = prev != null ? prev : v;
                        out.loads.put(out.arrayRefKey(baseArr, idx), previous);

                        if (!arrPased) {
                            if (prev != null) {
                                out.aliasMap.put(v, prev);
                            } else {
                                out.aliasMap.remove(v);
                            }
                        } else {
                            out.aliasMap.remove(v);
                        }

                    } 
                }

                else if (lop instanceof InstanceFieldRef) {
                    
                    InstanceFieldRef inf = (InstanceFieldRef) lop;
                    if (rop instanceof Local) {
                        // v.f = w
                        Local rigLocal = (Local) rop;
                        Local base = (Local) inf.getBase();

                        Set<Integer> basePointer = in.objMap.getOrDefault(base, new HashSet<>());
                    
                        Set<Integer> valuePointer = in.objMap.getOrDefault(rigLocal, new HashSet<>());

                        boolean isStrong = (basePointer.size() == 1 && basePointer.iterator().next() > 0 && !in.passVar.contains(basePointer.iterator().next()));

                        for(Integer objNo: basePointer) {
                            String key = objNo + "." + inf.getField().getSignature();
                            
                            if (isStrong) {
                                out.fieldMap.put(key, new HashSet<>(valuePointer));
                            } else {
                                Set<Integer> targetPointer = out.fieldMap.get(key);

                                if (targetPointer == null) {
                                    targetPointer = new HashSet<>();
                                    out.fieldMap.put(key, targetPointer);
                                }

                                // Set<Integer> fPtr = out.fieldMap.computeIfAbsent(key, t -> new HashSet<>());
                                // fPtr.addAll(valuePointer);
                                targetPointer.addAll(valuePointer);
                            }
                        }

                        SootField sfi = inf.getField();

                        for (int id: basePointer) {
                            if (in.passVar.contains(id)) {
                                String newK = 0  + "." + sfi.getSignature();
                                Set<Integer> ptrZ = out.fieldMap.getOrDefault(newK, new HashSet<>());
                                ptrZ.addAll(valuePointer);
                                out.fieldMap.put(newK, ptrZ);
                                break;
                            }
                        } 


                        invalidateFields(base, sfi, out);

                        if (isStrong) {
                            // String k3 = base + "." + sfi;
                            // Local cVar = in.aliasMap.getOrDefault(rigLocal, rigLocal);
                            // out.loads.put(k3, cVar);

                            Local localVar = in.aliasMap.getOrDefault(rigLocal, rigLocal);

                            String key = out.getKey(base, sfi);

                            if (!localVar.getName().startsWith("$")) {
                                out.loads.put(key, localVar);
                            }
                        }
                    }
                
                }
                else if (lop instanceof ArrayRef) {
                    if (rop instanceof Local) {
                        ArrayRef ref = (ArrayRef) lop;
                        Local base = (Local) ref.getBase();
                        // Value idx = ref.getIndex();
                        Local rightOp = (Local) rop;
                        
                        Set<Integer> basePtr = in.objMap.getOrDefault(base, new HashSet<>());
                        Set<Integer> valuePtr = in.objMap.getOrDefault(rightOp, new HashSet<>());
                        
                        for (Integer id : basePtr) {
                            String arrKey = id + ".[]";
                            Set<Integer> storePointer = out.fieldMap.get(arrKey);
                            
                            if (storePointer == null) {
                                storePointer = new HashSet<>();
                                out.fieldMap.put(arrKey, storePointer);
                            }
                            storePointer.addAll(valuePtr);
                        }
                        
                        invalidateArrayLoads(base, out);
                    }
                }
            
            } else if (((Stmt) unit).containsInvokeExpr()) {
                // out.loads.clear();
                // out.aliasMap.clear();
                
                Stmt stmt = (Stmt) unit;
                InvokeExpr iExpr = stmt.getInvokeExpr();

                if (iExpr.getMethod().getName().equals("<init>")) {
                    return;
                }

                Set<Integer> calledOn = new HashSet<>();
                // Set<Integer> reach = new HashSet<>();
                List<Value> args = new ArrayList<>(iExpr.getArgs());

                if (iExpr instanceof InstanceInvokeExpr) {
                    InstanceInvokeExpr iie = (InstanceInvokeExpr) iExpr;
                    args.add(iie.getBase());
                }

                for (Value arg: args) {
                    if (arg instanceof Local) {
                        Local currArgs = (Local) arg;
                        calledOn.addAll(out.objMap.getOrDefault(currArgs, new HashSet<>()));
                    }
                }

                List<Integer> nodes = new ArrayList<>(calledOn);
                Set<Integer> reachable = new HashSet<>(calledOn);
                int head = 0;

                while (head < nodes.size()) {
                    int varId = nodes.get(head++);

                    if (varId == 0) continue;

                    for (String key : out.fieldMap.keySet()) {
                        if (key.startsWith(varId + ".")) {
                            Set<Integer> targets = out.fieldMap.get(key);

                            for (int targetId : targets) {
                                if (reachable.add(targetId)) {
                                    nodes.add(targetId);
                                }
                            }
                        }
                    }
                }

                out.passVar.addAll(reachable);

                Set<String> passedVar = new HashSet<>();

                for (Local loc: body.getLocals()) {
                    Set<Integer> bPt = out.objMap.get(loc);

                    if (bPt!=null) {
                        for (int id: bPt) {
                            if (out.passVar.contains(id)) {
                                passedVar.add(loc.getName());
                                break;
                            }
                        }
                    }
                }

                // out.loads.entrySet().removeIf(entry ->)

                List<String> removeKeys = new ArrayList<>();
                
                for(String key: out.loads.keySet()) {
                    String baseName = key.split("\\.")[0].split("\\[")[0];

                    if (passedVar.contains(baseName)) {
                        removeKeys.add(key);
                    }
                }

                for (String key: removeKeys) {
                    out.loads.remove(key);
                }
            }
        }

        private void invalidateArrayLoads(Local arr, PointToGraph out) {

            String pref = out.aliasMap.getOrDefault(arr, arr).getName() + "[";
            Iterator<Map.Entry<String, Local>> it = out.loads.entrySet().iterator();

            while (it.hasNext()) {
                Map.Entry<String, Local> e = it.next();
                // if (e.getKey().endsWith(".[]")) {
                if(e.getKey().startsWith(pref))
                    it.remove();
                }
            }

        // @Override
        // protected void copy(AnalysisTransformer.PointToGraph arg0, AnalysisTransformer.PointToGraph arg1) {
        //     // TODO Auto-generated method stub
        //     throw new UnsupportedOperationException("Unimplemented method 'copy'");
        // }

        // @Override
        // protected void merge(AnalysisTransformer.PointToGraph arg0, AnalysisTransformer.PointToGraph arg1,
        //         AnalysisTransformer.PointToGraph arg2) {
        //     // TODO Auto-generated method stub
        //     throw new UnsupportedOperationException("Unimplemented method 'merge'");
        // }

        // @Override
        // protected AnalysisTransformer.PointToGraph newInitialFlow() {
        //     // TODO Auto-generated method stub
        //     throw new UnsupportedOperationException("Unimplemented method 'newInitialFlow'");
        // }
        

        private void invalidateFields(Local base, SootField sf, PointToGraph out) {
            Set<Integer> basePtr = out.objMap.getOrDefault(base, new HashSet<>());
            List<String> toKill = new ArrayList<>();

            for (String key: out.loads.keySet()) {
                if (key.endsWith("." + sf.getSignature())) {
                    String otherBase = key.split("\\.")[0];
                    Local otherLocal = null;

                    for (Local l: body.getLocals()) {
                        if (l.getName().equals(otherBase)) {
                            otherLocal = l;
                            break;
                        }
                    }
                    
                    if (otherLocal == null) {
                        toKill.add(key);
                        continue;
                    }

                    Set<Integer> otherPtr = out.objMap.getOrDefault(otherLocal, new HashSet<>());

                    boolean mayAlias = false;

                    for (int id: basePtr) {
                        if (otherPtr.contains(id)) {
                            mayAlias = true;
                            break;
                        }
                    }

                    if (mayAlias) {
                        toKill.add(key);
                    }

                }
            }
            
            for (String k: toKill) {
                out.loads.remove(k);
            }
        }


        @Override
        protected void copy(PointToGraph arg0, PointToGraph arg1) {
            arg1.moveFlowFacts(arg0);
            // throw new UnsupportedOperationException("Unimplemented method 'copy'");
        }


        @Override
        protected void merge(PointToGraph in1, PointToGraph in2, PointToGraph out) {
            // throw new UnsupportedOperationException("Unimplemented method 'merge'");
            if (in1.top) {
                out.moveFlowFacts(in2);
                return;
            }

            if (in2.top) {
                out.moveFlowFacts(in1);
                return;
            }

            out.top = false;

            out.objMap.clear();

            Set<Local> varibles = new HashSet<>();
            varibles.addAll(in1.objMap.keySet());
            varibles.addAll(in2.objMap.keySet());

            for (Local v : varibles) {
                Set<Integer> s1 = in1.objMap.getOrDefault(v, new HashSet<>());
                Set<Integer> s2 = in2.objMap.getOrDefault(v, new HashSet<>());
                Set<Integer> group = new HashSet<>(s1);

                group.addAll(s2);
                out.objMap.put(v, group);
            }

            // Field Map

            out.fieldMap.clear();
            Set<String> allFieldKeys = new HashSet<>();
            allFieldKeys.addAll(in1.fieldMap.keySet());
            allFieldKeys.addAll(in2.fieldMap.keySet());
          
            for (String key : allFieldKeys) {
                Set<Integer> s1 = in1.fieldMap.getOrDefault(key, new HashSet<>());
                Set<Integer> s2 = in2.fieldMap.getOrDefault(key, new HashSet<>());
                Set<Integer> group = new HashSet<>(s1);

                group.addAll(s2);

                out.fieldMap.put(key, group);
            }

            // avail loads

            out.loads.clear();

            for (String key : in1.loads.keySet()) {
                Local loc1 = in1.loads.get(key);
                Local loc2 = in2.loads.get(key);

                if (loc1 != null && loc1.equals(loc2)) {
                    out.loads.put(key, loc1);
                }
            }

            out.aliasMap.clear();

            for (Local l : in1.aliasMap.keySet()) {
                Local f = in1.aliasMap.get(l);
                Local s = in2.aliasMap.get(l);

                if (f!=null && f.equals(s)) {
                    out.aliasMap.put(l, f);
                }
            }

            out.passVar.clear();
            out.passVar.addAll(in1.passVar);
            out.passVar.addAll(in2.passVar);
        }


        @Override
        protected PointToGraph newInitialFlow() {
            return new PointToGraph(true);
        }
        
        @Override
        protected PointToGraph entryInitialFlow() {
            // TODO Auto-generated method stub
            PointToGraph initialgraph = new PointToGraph();
            initialgraph.top = false;
            int counter = -1;

            // Local thisRef = body.getThisLocal();
            // Set<Integer> ptr = new HashSet<>();
            // ptr.add(counter--);
            
            // initialgraph.objMap.put(thisRef, ptr);

            for (Local localParam: body.getParameterLocals()) {
                if (localParam.getType() instanceof RefLikeType) {
                    Set<Integer> pointer = new HashSet<>();
                    pointer.add(counter--);
                    initialgraph.objMap.put(localParam, pointer);
                }
            }

            return initialgraph;
        }
    
        void invalidateAvailLoadDest(Local variable, PointToGraph pg) {
            pg.loads.values().removeIf(t->t.equals(variable));

            String prefix = variable.getName() + ".";

            pg.loads.keySet().removeIf(t -> t.startsWith(prefix));    
            
            pg.aliasMap.remove(variable);
        }

        public List<String> printRedundant() {
            // if(!redundant.isEmpty()) {
            //     System.out.println("CLASS: " + this.sootClass);
            //     System.out.println("  METHOD: "+ this.sootMethod);

            //     for (String r: redundant) {
            //         System.out.println("  "+ r);
            //     }
            // }

            Map<Unit, String> redundantLoads = new LinkedHashMap<>();

            for (Unit u : body.getUnits()) {
                if (u instanceof AssignStmt) {
                    AssignStmt as = (AssignStmt) u;
                    Value left = as.getLeftOp();
                    Value right = as.getRightOp();
                    
                    PointToGraph inGraph = getFlowBefore(u);

                    if (left instanceof Local) {
                        if (right instanceof InstanceFieldRef) {
                            InstanceFieldRef fieldRef = (InstanceFieldRef) right;
                            Local base = (Local) fieldRef.getBase();
                            SootField field = fieldRef.getField();

                            // String k6 = base + "." + field;
                            String k6 = inGraph.getKey(base, field);

                            Local preentry = inGraph.loads.get(k6);

                            if (preentry !=null && !preentry.equals(left) && !preentry.getName().startsWith("$")) {
                                int lineNum = u.getJavaSourceStartLineNumber();
                                String fieldSig = field.getSignature().replace(": ",  ":");
                                redundantLoads.put(u, lineNum + ":" + base.getName() + "." + fieldSig + " " + preentry.getName());
                            }
                        }

                        else if (right instanceof ArrayRef) {
                            ArrayRef arrRef = (ArrayRef) right;
                            Local base = (Local) arrRef.getBase();

                            String newKey = inGraph.arrayRefKey(base, arrRef.getIndex());
                            Local provid = inGraph.loads.get(newKey);

                            if (provid != null && !provid.equals(left) && !provid.getName().startsWith("$")) {
                                int lno = u.getJavaSourceStartLineNumber();
                                String printFormat = lno + ":" + base.getName() + "[]" + provid.getName();
                                // redundantLoads.put(u, printFormat);
                            }
                        }
                    }

                }
            }

            List<String> results = new ArrayList<>(redundantLoads.values());
            Collections.sort(results);
            return results;
        }
    
    }

    public static void result() {

        for (String method : finalResult.keySet()) {
            System.out.println(method);

            for (String line : finalResult.get(method)) {
                System.out.println(line);
            }
        }
    }

    @Override
    protected void internalTransform(Body body, String phaseName, Map<String, String> options) {
        SootMethod sMethod = body.getMethod();
        UnitGraph uGraph = new BriefUnitGraph(body);
        
        PointToGraphAnalysis pga = new PointToGraphAnalysis(sMethod, sMethod.getName(), body, sMethod.getDeclaringClass().getName(), uGraph);

        // pga.printRedundant();

        List<String> redundants = pga.printRedundant();

        if (redundants.size() > 0) {
            String class_method = sMethod.getDeclaringClass().getName() + ":" + sMethod.getName();
            finalResult.put(class_method, redundants);
        }

        // System.out.println(finalResult.keySet());
        
        // System.out.println(PointToGraphAnalysis.finalResult);
    }
}