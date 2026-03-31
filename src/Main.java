import javax.swing.SwingUtilities;

public class Main {
    public static void main(String[] args) {
            SwingUtilities.invokeLater(() -> {
            EditorUI app = new EditorUI();
            app.initListeners();
            app.setVisible(true);
        });  
    }
}