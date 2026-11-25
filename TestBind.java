public class TestBind {
    public static void main(String[] args) {
        int[] ports = {8080, 8081, 8082, 8088};
        for (int p : ports) {
            try (java.net.ServerSocket ss = new java.net.ServerSocket(p)) {
                System.out.println("Java port " + p + ": SUCCESS");
            } catch (Exception e) {
                System.out.println("Java port " + p + ": " + e.getClass().getName() + " - " + e.getMessage());
            }
        }
    }
}
