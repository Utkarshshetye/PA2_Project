public class PointToGraphAnalysis {

    public static void main(String[] args) {
        
    }
}

// import java.net.URISyntaxException;
// import java.security.URIParameter;
// import java.util.ArrayList;
// import java.util.Collections;
// import java.util.HashMap;
// import java.util.HashSet;
// import java.util.Iterator;
// import java.util.LinkedHashMap;
// import java.util.List;
// import java.util.Map;
// import java.util.Objects;
// import java.util.Set;
// import java.util.TreeMap;

// import soot.Body;
// import soot.Local;
// import soot.RefLikeType;
// import soot.SootField;
// import soot.SootMethod;
// import soot.Unit;
// import soot.Value;
// import soot.jimple.ArrayRef;
// import soot.jimple.AssignStmt;
// import soot.jimple.InstanceFieldRef;
// import soot.jimple.IntConstant;
// import soot.jimple.NewArrayExpr;
// import soot.jimple.NewExpr;
// import soot.jimple.Stmt;
// import soot.toolkits.graph.UnitGraph;
// import soot.toolkits.scalar.ForwardFlowAnalysis;

// class PointToGraph {
//     // a -> Oi
//     Map<Local, Set<Integer>> objMap = new HashMap<>();
    
//     // field -> Oi's
//     Map<String, Set<Integer>> fieldMap = new HashMap<>();
    

//     Map<String, Local> loads = new HashMap<>();

//     // Alias map
//     Map<Local, Local> aliasMap = new HashMap<>();

//     boolean top = false;

//     public PointToGraph(boolean top) {
//         this.top = top;
//     }

//     public PointToGraph() {
//         //TODO Auto-generated constructor stub
//     }

//     public void moveFlowFacts(PointToGraph pg2) {
//         objMap = new HashMap<>();
//         fieldMap = new HashMap<>();
//         loads = new HashMap<>(pg2.loads);

//         for (Map.Entry<Local, Set<Integer>> iter: pg2.objMap.entrySet()) {
//             objMap.put(iter.getKey(), iter.getValue());
//         }
        
//         for(Map.Entry<String, Set<Integer>> iter2: pg2.fieldMap.entrySet()) {
//             fieldMap.put(iter2.getKey(), iter2.getValue());
//         }
//     }

//     @Override
//     public boolean equals(Object obj) {
//         PointToGraph pg = (PointToGraph) obj;

//         return pg.objMap.equals(this.objMap) && pg.fieldMap.equals(this.fieldMap) && pg.loads.equals(this.loads);
//     }

//     @Override
//     public int hashCode() {
//         // TODO Auto-generated method stub
//         return Objects.hash(objMap, fieldMap, loads, aliasMap, top);
//     }

//     public String arrayRefKey(Local b, Value idx) {
//         Local aliasValue = aliasMap.getOrDefault(b, b);
//         String arrIdx = "[]";

//         if (idx instanceof IntConstant) {
//             IntConstant constant = (IntConstant) idx;
//             arrIdx = "[" + constant.value + "]";
//         } else if (idx instanceof Local) {
//             Local newIdx = (Local) idx;
//             Local locIdx = aliasMap.getOrDefault(newIdx, newIdx);
//             arrIdx = "[" + locIdx.getName() + "]";
//         }

//         return aliasValue.getName() + arrIdx;
//     }
// }

// public class PointToGraphAnalysis extends ForwardFlowAnalysis<Unit, PointToGraph>{

//     String sootClass;
//     String sootMethod;
//     Body body;
//     List<String> redundant;
//     static Map<String, List<String>> finalResult = new TreeMap<>();

//     public PointToGraphAnalysis(SootMethod sMethod, String mName, Body body, String cName, UnitGraph uGraph) {
//         super(uGraph);
//         this.sootClass = cName;
//         this.sootMethod = mName;
//         this.body = uGraph.getBody();
//         doAnalysis();
//     }

//     @Override
//     protected void flowThrough(PointToGraph in, Unit unit, PointToGraph out) {
//         // Copy out graph to in
//         out.moveFlowFacts(in);
        
//         if (unit instanceof AssignStmt) {
//             // ... = ...
//             AssignStmt assignStmt = (AssignStmt) unit;
            
//             Value lop = assignStmt.getLeftOp();
//             Value rop = assignStmt.getRightOp();

//             if (rop instanceof NewExpr || rop instanceof NewArrayExpr) {
//                 // v = new T()

//                 if (lop instanceof Local) {
//                     //10. A a = new A(), o10
//                     Local lVar = (Local) lop;
//                     int objNo = assignStmt.getJavaSourceStartLineNumber();
                    
//                     Set<Integer> objsPntTo = new HashSet<>();
//                     objsPntTo.add(objNo);

//                     out.objMap.put(lVar, objsPntTo);
                    
//                     // If already some entry store, then remove, for field sensitivity
//                     // List<Local> locals = (List<Local>) out.loads.values();
                    
//                     // for (Local local : locals) {
//                     //     out.objMap.remove(local);
//                     // }

//                     // out.aliasMap.remove((Local) lop);

                    
//                 }
//             } 
            
//             else if (lop instanceof Local) {
//                 // field access, Node c = ...;
//                 Local v = (Local) lop;

//                 if (rop instanceof Local) {
//                     // v = w
//                     // copy, rhs to lhs

//                     Local w = (Local) rop;

//                     HashSet<Integer> pointing = (HashSet<Integer>) in.objMap.getOrDefault(w, new HashSet<>());
                    
//                     out.objMap.put(v, pointing);

//                     // If already some entry store, then remove, for field sensitivity
//                     List<Local> locals = (List<Local>) out.loads.values();
                    
//                     for (Local local : locals) {
//                         out.objMap.remove(local);
//                     }
                    
//                     Local exist = in.aliasMap.get(w);

//                     if (exist != null) {
//                         out.aliasMap.put(v, exist);
//                     } else {
//                         out.aliasMap.put(v, w);
//                     }
//                 }

//                 else if (rop instanceof InstanceFieldRef) {
//                     // v = w.f
//                     InstanceFieldRef instField = (InstanceFieldRef) rop;

//                     // If already entry present, redundant, replacement found
//                     Value base = instField.getBase();
//                     SootField second = instField.getField();
                    
//                     String availKey = base + "." + second;

//                     Local isPrent = in.loads.get(availKey);

//                     Set<Integer> bPtr = in.objMap.getOrDefault(base, new HashSet<>());
//                     Set<Integer> rPtr = new HashSet<>();
                    
//                     for (int id : bPtr) {
//                         String key = id + "." + second.getSignature();
//                         Set<Integer> values = in.fieldMap.get(key);

//                         if (values!=null) {
//                             rPtr.addAll(values);
//                         }
//                     }
                    
//                     out.objMap.put((Local) lop, rPtr);

//                     // invalidate available loads destination
//                     out.loads.values().removeIf(t->t.equals((Local) lop));

//                     Local provi = (isPrent != null) ? isPrent : (Local) lop;

//                     String k2 = base + "." + second;

//                     out.loads.put(k2, provi);

//                     if (isPrent!=null) {
//                         out.aliasMap.put(v, isPrent);
//                     } else {
//                         out.aliasMap.remove(v);
//                     }


//                     // if (isPrent != null) {
//                     //     int lno = unit.getJavaSourceStartLineNumber();
                        
//                     //     String op = "Line " + lno + ": " + ((Local)instField.getBase()).getName() + "." + instField.getField().getName() + " (can be replaced with " + isPrent.getName() + ")";
                        
//                     //     redundant.add(op);
//                     // }

//                     // Local base = (Local) instField.getBase();

//                     // Set<Integer> ptr = in.objMap.getOrDefault(base, Collections.emptySet());

//                     // Set<Integer> res = new HashSet<>();

//                     // for (Integer integer : ptr) {
//                     //     String key = integer + "." + instField.getField().getSignature();

//                     //     res.addAll(in.fieldMap.getOrDefault(key, Collections.emptySet()));
//                     // }

//                     // out.objMap.put((Local) lop, res);

//                     // String newKey = base.getName() + "." + instField.getField().getSignature();

//                     // List<Local> locals = (List<Local>) out.loads.values();
                    
//                     // for (Local local : locals) {
//                     //     out.objMap.remove(local);
//                     // }
                    
//                     // out.loads.put(newKey, (Local) lop);
                
//                 } else if (rop instanceof ArrayRef) {
//                     // x = arr[]
                    
//                     ArrayRef arrRef = (ArrayRef) rop;
//                     Local baseArr = (Local) arrRef.getBase();
//                     Value idx = arrRef.getIndex();

//                     String key = in.arrayRefKey(baseArr, idx);
//                     Local prev = in.loads.get(key);

//                     // String arrKey = 
//                     Set<Integer> arrPointer = in.objMap.getOrDefault(baseArr, new HashSet<>());
//                     Set<Integer> arrRes = new HashSet<>();
                    
//                     for (Integer id : arrPointer) {
//                         String arrFieldKey = id + ".[]";
//                         Set<Integer> contents = in.fieldMap.get(arrFieldKey);

//                         if(!contents.isEmpty()) {
//                             arrRes.addAll(contents);
//                         }
//                     }

//                     out.objMap.put(v, arrRes);

//                     invalidateArrayLoads(v, out);

//                     Local previous = prev != null ? prev : v;
//                     out.loads.put(out.arrayRefKey(baseArr, idx), previous);

//                     if (prev != null) {
//                         out.aliasMap.put(v, prev);
//                     } else {
//                         out.aliasMap.remove(v);
//                     }

//                 } 
//             }

//             else if (lop instanceof InstanceFieldRef) {
                
//                 InstanceFieldRef inf = (InstanceFieldRef) lop;
//                 if (rop instanceof Local) {
//                     // v.f = w
//                     Local rigLocal = (Local) rop;
//                     Local base = (Local) inf.getBase();

//                     Set<Integer> basePointer = in.objMap.getOrDefault(base, new HashSet<>());
//                     Set<Integer> valuePointer = in.objMap.getOrDefault(rigLocal, new HashSet<>());

//                     boolean isStrong = (basePointer.size() == 1 && basePointer.iterator().next() > 0);

//                     for(Integer objNo: basePointer) {
//                         String key = objNo + "." + inf.getField().getSignature();
                        
//                         if (isStrong) {
//                             out.fieldMap.put(key, new HashSet<>(valuePointer));
//                         } else {
//                             Set<Integer> targetPointer = out.fieldMap.get(key);

//                             if (targetPointer == null) {
//                                 targetPointer = new HashSet<>();
//                                 out.fieldMap.put(key, targetPointer);
//                             }

//                             // Set<Integer> fPtr = out.fieldMap.computeIfAbsent(key, t -> new HashSet<>());
//                             // fPtr.addAll(valuePointer);
//                             targetPointer.addAll(valuePointer);
//                         }
//                     }

//                     SootField sfi = inf.getField();

//                     invalidateFields(base, sfi, out);

//                     if (isStrong) {
//                         String k3 = base + "." + sfi;
//                         out.loads.put(k3, rigLocal);
//                     }
//                 }
//             }

//             else if (lop instanceof ArrayRef) {
//                 if (rop instanceof Local) {
//                     ArrayRef ref = (ArrayRef) lop;
//                     Local base = (Local) ref.getBase();
//                     Value idx = ref.getIndex();
//                     Local rightOp = (Local) rop;

//                     Set<Integer> basePtr = in.objMap.getOrDefault(base, new HashSet<>());
//                     Set<Integer> valuePtr = in.objMap.getOrDefault(rightOp, new HashSet<>());

//                     for (Integer id : basePtr) {
//                         String arrKey = id + ".[]";
//                         Set<Integer> storePointer = out.fieldMap.get(arrKey);

//                         if (storePointer == null) {
//                             storePointer = new HashSet<>();
//                             out.fieldMap.put(arrKey, storePointer);
//                         }
//                         storePointer.addAll(valuePtr);
//                     }

//                     invalidateArrayLoads(base, out);
//                 }
//             }

//             else if (((Stmt) unit).containsInvokeExpr()) {
//                 out.loads.clear();
//                 out.aliasMap.clear();
//             }
//         }
//     }

//     private void invalidateArrayLoads(Local arr, PointToGraph out) {

//         Iterator<Map.Entry<String, Local>> it = out.loads.entrySet().iterator();

//         while (it.hasNext()) {
//             Map.Entry<String, Local> e = it.next();
//             if (e.getKey().endsWith(".[]")) {
//                 it.remove();
//             }
//         }
//     }

//     private void invalidateFields(Local base, SootField sf, PointToGraph out) {

//         Iterator<Map.Entry<String, Local>> it = out.loads.entrySet().iterator();

//         while (it.hasNext()) {
//             Map.Entry<String, Local> e = it.next();
//             if (e.getKey().endsWith("." + sf.getSignature())) {
//                 it.remove();
//             }
//         }
//     }


//     @Override
//     protected void copy(PointToGraph arg0, PointToGraph arg1) {
//         arg1.moveFlowFacts(arg0);
//         // throw new UnsupportedOperationException("Unimplemented method 'copy'");
//     }


//     @Override
//     protected void merge(PointToGraph in1, PointToGraph in2, PointToGraph out) {
//         // throw new UnsupportedOperationException("Unimplemented method 'merge'");
//         if (in1.top) {
//             out.moveFlowFacts(in2);
//             return;
//         }

//         if (in2.top) {
//             out.moveFlowFacts(in1);
//             return;
//         }

//         out.top = false;

//         out.objMap.clear();

//         Set<Local> varibles = new HashSet<>();
//         varibles.addAll(in1.objMap.keySet());
//         varibles.addAll(in2.objMap.keySet());

//         for (Local v : varibles) {
//             Set<Integer> s1 = in1.objMap.getOrDefault(v, new HashSet<>());
//             Set<Integer> s2 = in2.objMap.getOrDefault(v, new HashSet<>());
//             Set<Integer> group = new HashSet<>(s1);

//             group.addAll(s2);
//             out.objMap.put(v, group);
//         }

//         // Field Map

//         out.fieldMap.clear();
//         Set<String> allFieldKeys = new HashSet<>();
//         allFieldKeys.addAll(in1.fieldMap.keySet());
//         allFieldKeys.addAll(in2.fieldMap.keySet());

//         for (String key : allFieldKeys) {
//             Set<Integer> s1 = in1.fieldMap.getOrDefault(key, new HashSet<>());
//             Set<Integer> s2 = in2.fieldMap.getOrDefault(key, new HashSet<>());
//             Set<Integer> group = new HashSet<>(s1);

//             group.addAll(s2);

//             out.fieldMap.put(key, group);
//         }

//         // avail loads

//         out.loads.clear();

//         for (String key : in1.loads.keySet()) {
//             Local loc1 = in1.loads.get(key);
//             Local loc2 = in2.loads.get(key);

//             if (loc1 != null && loc1.equals(loc2)) {
//                 out.loads.put(key, loc1);
//             }
//         }

//         out.aliasMap.clear();

//         for (Local l : in1.aliasMap.keySet()) {
//             Local f = in1.aliasMap.get(l);
//             Local s = in2.aliasMap.get(l);

//             if (f!=null && f.equals(s)) {
//                 out.aliasMap.put(l, f);
//             }
//         }

//     }


//     @Override
//     protected PointToGraph newInitialFlow() {
//         return new PointToGraph(true);
//     }
    
//     @Override
//     protected PointToGraph entryInitialFlow() {
//         // TODO Auto-generated method stub
//         PointToGraph initialgraph = new PointToGraph();
//         int counter = -1;

//         // Local thisRef = body.getThisLocal();
//         // Set<Integer> ptr = new HashSet<>();
//         // ptr.add(counter--);
        
//         // initialgraph.objMap.put(thisRef, ptr);

//         for (Local localParam: body.getParameterLocals()) {
//             if (localParam.getType() instanceof RefLikeType) {
//                 Set<Integer> pointer = new HashSet<>();
//                 pointer.add(counter);
//                 initialgraph.objMap.put(localParam, pointer);
//             }
//         }

//         return initialgraph;
//     }

//     public List<String> printRedundant() {
//         // if(!redundant.isEmpty()) {
//         //     System.out.println("CLASS: " + this.sootClass);
//         //     System.out.println("  METHOD: "+ this.sootMethod);

//         //     for (String r: redundant) {
//         //         System.out.println("  "+ r);
//         //     }
//         // }

//         Map<Unit, String> redundantLoads = new LinkedHashMap<>();

//         for (Unit u : body.getUnits()) {
//             if (u instanceof AssignStmt) {
//                 AssignStmt as = (AssignStmt) u;
//                 Value left = as.getLeftOp();
//                 Value right = as.getRightOp();

//                 PointToGraph inGraph = getFlowBefore(u);

//                 if (left instanceof Local) {
//                     if (right instanceof InstanceFieldRef) {
//                         InstanceFieldRef fieldRef = (InstanceFieldRef) right;
//                         Local base = (Local) fieldRef.getBase();
//                         SootField field = fieldRef.getField();

//                         String k6 = base + "." + field;

//                         Local preentry = inGraph.loads.get(k6);

//                         if (preentry !=null && !preentry.equals(left)) {
//                             int lineNum = u.getJavaSourceStartLineNumber();
//                             String fieldSig = field.getSignature().replace(": ",  ":");
//                             redundantLoads.put(u, lineNum + ":" + base.getName() + "." + fieldSig + " " + preentry.getName());
//                         }
//                     }

//                     else if (right instanceof ArrayRef) {
//                         ArrayRef arrRef = (ArrayRef) right;
//                         Local base = (Local) arrRef.getBase();

//                         String newKey = inGraph.arrayRefKey(base, arrRef.getIndex());
//                         Local provid = inGraph.loads.get(newKey);

//                         if (provid != null && !provid.equals(left)) {
//                             int lno = u.getJavaSourceStartLineNumber();
//                             String printFormat = lno + ":" + base.getName() + "[]" + provid.getName();
//                             redundantLoads.put(u, printFormat);
//                         }
//                     }
//                 }

//             }
//         }

//         List<String> results = new ArrayList<>(redundantLoads.values());
//         Collections.sort(results);
//         return results;
//     }
// }
