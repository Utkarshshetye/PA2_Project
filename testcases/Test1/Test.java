class Node {
	Node f1;
	Node f2;
	Node g;
	Node() {}
}

public class Test {
	Test f;
	int f1;
	static class A {
		public A f1;

		
	}
	
	public void foo() {
	}

	// public void testWithinIteration(Node a) {
	// 	for (int i = 0; i<10; i++) {
	// 		Node x = a.next;
	// 		Node y = a.next;
	// 		System.out.println(x);
	// 		System.out.println(y);
	// 	}
	// }

	// public void testWithModification(Node a) {
	// 	for(int i=0; i<10; i++) {
	// 		Node x = a.next;
	// 		a.next = new Node();
	// 		Node y = a.next;
	// 	}
	// }

	// public void loopRedundant() {
	// 	Node a = new Node();
	// 	Node b = new Node();

	// 	for (int i = 0; i < 10; i++) {
	// 		Node x = a.next;
	// 		Node y = a.next;
	// 		Node p = b.next;
	// 		Node q = b.next;
	// 	}

	// 	Node z = a.next;
	// 	Node w = a.next;
	// }

	public static void main(String[] args) {
		// Node a = new Node();
		// a.f1 = new Node();
		// Node b = new Node();
		// b.f1 = new Node();
		// a.f2 = new Node();
		// Node c = a.f1;
		// a.f2 = a.f1;
		// b.f1 = a.f2;
		// Test a = new Test();
		// a.f = new Test();
		// a.f.f = new Test();
		// Test o1 = a.f.f;
		// // foo(a);
		// Test o2 = a.f.f;
		
		Test a,b,c,d,e;
		a = new Test();
		b = new Test();
		b.f = new Test();
		a.f = new Test();
		a=b.f;
		d=a.f;
		// a.foo();
		c=b.f;
		e=a.f;

		// Test a = new Test();
		// Test h = new Test();

		// a.f1 = 10;
		// h.f1 = 17;

		// int x = h.f1;
		// int b = x + 11;

		// int c = a.f1;

		// System.out.println(b);

		// Node[] arr = new Node[10];
		// arr[0] = new Node();

		// Node a = arr[0];
		// Node b = arr[0];

		// Node c = arr[1];
		// Node d = arr[1];
	}
}
