import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Locale;
import javax.swing.JFileChooser;
import javax.swing.JFrame;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.text.rtf.RTFEditorKit;

public class GerenciadorArquivos {

    public static class ResultadoAbertura {
        public final Document document;
        public final String fileName;

        public ResultadoAbertura(Document document, String fileName) {
            this.document = document;
            this.fileName = fileName;
        }
    }

    public ResultadoAbertura abrirArquivo(JFrame parent) throws IOException, BadLocationException {
        JFileChooser seletor = new JFileChooser();
        FileNameExtensionFilter allFilter = new FileNameExtensionFilter("Todos (*.txt, *.rtf)", "txt", "rtf");
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File Document (*.txt)", "txt");
        FileNameExtensionFilter rtfFilter = new FileNameExtensionFilter("Rich Text Format (*.rtf)", "rtf");
        seletor.addChoosableFileFilter(allFilter);
        seletor.addChoosableFileFilter(txtFilter);
        seletor.addChoosableFileFilter(rtfFilter);
        seletor.setFileFilter(allFilter);
        seletor.setAcceptAllFileFilterUsed(false);

        int opcao = seletor.showOpenDialog(parent);
        if (opcao != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File arquivo = seletor.getSelectedFile();
        String lower = arquivo.getName().toLowerCase(Locale.ROOT);

        if (lower.endsWith(".rtf")) {
            RTFEditorKit rtfKit = new RTFEditorKit();
            Document doc = rtfKit.createDefaultDocument();
            try (FileInputStream fis = new FileInputStream(arquivo)) {
                rtfKit.read(fis, doc, 0);
            }
            return new ResultadoAbertura(doc, arquivo.getName());
        }

        // Texto simples
        // Lê o arquivo como texto puro e insere no documento para evitar problemas com o
        // comportamento de `JTextPane.read(...)` em alguns ambientes.
        StringBuilder conteudo = new StringBuilder();
        try (BufferedReader leitor = new BufferedReader(new java.io.InputStreamReader(
                new java.io.FileInputStream(arquivo), StandardCharsets.UTF_8))) {
            String linha;
            while ((linha = leitor.readLine()) != null) {
                conteudo.append(linha).append("\n");
            }
        }

        javax.swing.text.DefaultStyledDocument doc = new javax.swing.text.DefaultStyledDocument();
        doc.insertString(0, conteudo.toString(), null);
        return new ResultadoAbertura(doc, arquivo.getName());
    }

    public String salvarArquivo(JFrame parent, Document doc) throws IOException, BadLocationException {
        JFileChooser seletor = new JFileChooser();
        FileNameExtensionFilter txtFilter = new FileNameExtensionFilter("Text File Document (*.txt)", "txt");
        FileNameExtensionFilter rtfFilter = new FileNameExtensionFilter("Rich Text Format (*.rtf)", "rtf");
        seletor.addChoosableFileFilter(txtFilter);
        seletor.addChoosableFileFilter(rtfFilter);
        seletor.setFileFilter(txtFilter);
        seletor.setAcceptAllFileFilterUsed(false);

        int opcao = seletor.showSaveDialog(parent);
        if (opcao != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File arquivo = seletor.getSelectedFile();
        String selectedName = arquivo.getName();
        String lower = selectedName.toLowerCase(Locale.ROOT);

        // Se o usuário não especificou uma extensão, adiciona com base no filtro selecionado.
        javax.swing.filechooser.FileFilter filtroSelecionado = seletor.getFileFilter();
        if (filtroSelecionado instanceof FileNameExtensionFilter extFilter) {
            if (!lower.contains(".")) {
                String ext = extFilter.getExtensions()[0];
                arquivo = new File(arquivo.getParentFile(), selectedName + "." + ext);
                lower = arquivo.getName().toLowerCase(Locale.ROOT);
            }
        }

        if (lower.endsWith(".rtf")) {
            RTFEditorKit rtfKit = new RTFEditorKit();
            try (FileOutputStream fos = new FileOutputStream(arquivo)) {
                rtfKit.write(fos, doc, 0, doc.getLength());
            }
        } else {
            // Salva o texto puro do documento, preservando o conteúdo mesmo quando houver estilo.
            String texto = doc.getText(0, doc.getLength());
            try (java.io.BufferedWriter escritor = new java.io.BufferedWriter(
                new java.io.OutputStreamWriter(new FileOutputStream(arquivo), StandardCharsets.UTF_8))) {
                escritor.write(texto);
            }
        }

        return arquivo.getName();
    }
}