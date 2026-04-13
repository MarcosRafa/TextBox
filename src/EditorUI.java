import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GraphicsEnvironment;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.InputEvent;
import java.awt.event.KeyEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import javax.swing.AbstractAction;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComboBox;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JMenu;
import javax.swing.JMenuBar;
import javax.swing.JMenuItem;
import javax.swing.JPanel;
import javax.swing.JRadioButtonMenuItem;
import javax.swing.JScrollPane;
import javax.swing.JSpinner;
import javax.swing.JTabbedPane;
import javax.swing.JTextPane;
import javax.swing.KeyStroke;
import javax.swing.SpinnerNumberModel;
import javax.swing.SwingUtilities;
import javax.swing.UIManager;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.text.BadLocationException;
import javax.swing.text.Document;
import javax.swing.undo.CannotRedoException;
import javax.swing.undo.CannotUndoException;
import javax.swing.undo.UndoManager;

public class EditorUI extends JFrame implements ActionListener {
    private final Corretor corretor = new Corretor();
    private final GerenciadorArquivos gi = new GerenciadorArquivos();

    // componentes
    private final JTabbedPane tabbedPane;
    private final List<TabInfo> tabs = new ArrayList<>();

    private final JComboBox<String> comboFontes;
    private final JSpinner spinnerTamanho;
    private final JCheckBox cbNegrito;
    private final JCheckBox cbItalico;
    private final String[] fontes;
    private final JMenuBar barraMenu;
    private final JMenu menuArquivo;
    private final JMenuItem itemNovo;
    private final JMenuItem itemAbrir;
    private final JMenuItem itemSalvar;
    private final JMenuItem itemSair;

    private final JMenu menuEditar;
    private final JMenuItem itemBuscar;

    private final JMenu menuTema;
    private final JRadioButtonMenuItem itemTemaClaro;
    private final JRadioButtonMenuItem itemTemaEscuro;

    private final JPanel statusBar;
    private final JLabel statusLabel;

    private String lastSearchTerm = null;
    private int lastSearchIndex = -1;

    private boolean isAutoCorrigindo = false;

    // estado usado para evitar re-autocorreção imediata da mesma palavra no mesmo local
    private int lastCorrectionStart = -1;
    private String lastCorrectedWordLower = null;
    private boolean skipNextReCorrection = false;

    private final String baseTitle = "TextBox";

    private enum Theme { CLARO, ESCURO }
    private Theme currentTheme = Theme.CLARO;

    private static final class TabInfo {
        final JTextPane textPane;
        final JLabel titleLabel;
        final JButton closeButton;
        final UndoManager undoManager = new UndoManager();
        String fileName;
        boolean dirty;

        TabInfo(JTextPane textPane, String fileName) {
            this.textPane = textPane;
            this.fileName = fileName;
            this.dirty = false;
            this.titleLabel = new JLabel();
            this.closeButton = new JButton("\u2715");
            this.closeButton.setBorderPainted(false);
            this.closeButton.setContentAreaFilled(false);
            this.closeButton.setFocusable(false);
            this.closeButton.setOpaque(false);
            this.closeButton.setPreferredSize(new Dimension(18, 18));
            this.closeButton.setMargin(new java.awt.Insets(0, 0, 0, 0));
            this.closeButton.setFont(this.closeButton.getFont().deriveFont(12f));
            this.closeButton.setToolTipText("Fechar aba");
        }

        String getDisplayName() {
            String name = (fileName == null) ? "Sem título" : new File(fileName).getName();
            return dirty ? name + " *" : name;
        }
    }

    private static final class WrapEditorKit extends javax.swing.text.StyledEditorKit {
        private final javax.swing.text.ViewFactory delegateFactory = super.getViewFactory();
        private final javax.swing.text.ViewFactory defaultFactory = elem -> {
            String kind = elem.getName();
            if (kind != null) {
                return switch (kind) {
                    case javax.swing.text.AbstractDocument.ContentElementName -> new WrapLabelView(elem);
                    case javax.swing.text.AbstractDocument.ParagraphElementName -> new javax.swing.text.ParagraphView(elem) {
                        @Override
                        public int getFlowSpan(int index) {
                            java.awt.Container c = getContainer();
                            if (c != null) {
                                return c.getWidth();
                            }
                            return super.getFlowSpan(index);
                        }
                    };
                    case javax.swing.text.AbstractDocument.SectionElementName -> new javax.swing.text.BoxView(elem, javax.swing.text.View.Y_AXIS);
                    case javax.swing.text.StyleConstants.ComponentElementName -> new javax.swing.text.ComponentView(elem);
                    case javax.swing.text.StyleConstants.IconElementName -> new javax.swing.text.IconView(elem);
                    default -> delegateFactory.create(elem);
                };
            }
            return delegateFactory.create(elem);
        };

        @Override
        public javax.swing.text.ViewFactory getViewFactory() {
            return defaultFactory;
        }
    }

    private static final class WrapLabelView extends javax.swing.text.LabelView {
        WrapLabelView(javax.swing.text.Element elem) {
            super(elem);
        }

        @Override
        public float getMinimumSpan(int axis) {
            if (axis == javax.swing.text.View.X_AXIS) {
                return 0;
            }
            return super.getMinimumSpan(axis);
        }

        @Override
        public int getBreakWeight(int axis, float pos, float len) {
            if (axis == javax.swing.text.View.X_AXIS) {
                return GoodBreakWeight;
            }
            return super.getBreakWeight(axis, pos, len);
        }

        @Override
        public javax.swing.text.View breakView(int axis, int offset, float pos, float len) {
            if (axis == javax.swing.text.View.X_AXIS) {
                return super.breakView(axis, offset, pos, len);
            }
            return this;
        }
    }

    private static final class WrappingTextPane extends JTextPane {
        WrappingTextPane() {
            setEditorKit(new WrapEditorKit());
        }

        @Override
        public boolean getScrollableTracksViewportWidth() {
            if (getParent() instanceof javax.swing.JViewport) {
                return true;
            }
            return super.getScrollableTracksViewportWidth();
        }

        @Override
        public void setSize(Dimension d) {
            if (getParent() instanceof javax.swing.JViewport viewport) {
                d = new Dimension(viewport.getWidth(), d.height);
            }
            super.setSize(d);
        }
    }

    private JPanel createTabComponent(TabInfo tab) {
        JPanel panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        panel.setOpaque(false);
        tab.titleLabel.setText(tab.getDisplayName());
        panel.add(tab.titleLabel);

        tab.closeButton.addActionListener(e -> closeTab(tabs.indexOf(tab)));
        panel.add(tab.closeButton);
        return panel;
    }

    private void updateTabTitle(TabInfo tab) {
        int idx = tabs.indexOf(tab);
        if (idx >= 0) {
            tab.titleLabel.setText(tab.getDisplayName());
        }
    }

    private void applyTheme(Theme theme) {
        currentTheme = theme;

        // salva posição de cursor e seleção para preservar após atualizar UI
        int tabCount = tabs.size();
        int[] caretPos = new int[tabCount];
        int[] selStart = new int[tabCount];
        int[] selEnd = new int[tabCount];
        for (int i = 0; i < tabCount; i++) {
            JTextPane pane = tabs.get(i).textPane;
            caretPos[i] = pane.getCaretPosition();
            selStart[i] = pane.getSelectionStart();
            selEnd[i] = pane.getSelectionEnd();
        }

        // paleta de cores centralizada
        Color bgBase = (theme == Theme.ESCURO) ? new Color(45, 45, 45) : new Color(238, 238, 238);
        Color fgBase = (theme == Theme.ESCURO) ? Color.WHITE : Color.BLACK;
        Color inputBg = (theme == Theme.ESCURO) ? new Color(60, 63, 65) : Color.WHITE;
        Color tabBg = (theme == Theme.ESCURO) ? new Color(30, 30, 30) : new Color(238, 238, 238);
        Color tabSelected = (theme == Theme.ESCURO) ? new Color(60, 63, 65) : Color.WHITE;

        // configurações Globais (UIManager)
        if (theme == Theme.ESCURO) {
            UIManager.put("Panel.background", bgBase);
            UIManager.put("Label.foreground", fgBase);
            UIManager.put("CheckBox.background", bgBase);
            UIManager.put("CheckBox.foreground", fgBase);
            UIManager.put("ComboBox.background", inputBg);
            UIManager.put("ComboBox.foreground", fgBase);
            UIManager.put("Spinner.background", inputBg);
            UIManager.put("Spinner.foreground", fgBase);
            UIManager.put("FormattedTextField.background", inputBg);
            UIManager.put("FormattedTextField.foreground", fgBase);
            UIManager.put("TabbedPane.background", tabBg);
            UIManager.put("TabbedPane.foreground", fgBase);
            UIManager.put("TabbedPane.selected", tabSelected);
            UIManager.put("TabbedPane.contentAreaColor", tabBg);
            UIManager.put("TabbedPane.opaque", true);
            UIManager.put("OptionPane.background", bgBase);
            UIManager.put("OptionPane.messageForeground", fgBase);
            UIManager.put("OptionPane.buttonAreaBackground", bgBase);
            UIManager.put("OptionPane.buttonForeground", fgBase);
            UIManager.put("OptionPane.foreground", fgBase);
            UIManager.put("Button.background", inputBg);
            UIManager.put("Button.foreground", fgBase);
            UIManager.put("TextField.background", inputBg);
            UIManager.put("TextField.foreground", fgBase);
            UIManager.put("TextField.caretForeground", fgBase);
            UIManager.put("TextField.selectionBackground", new Color(75, 110, 175));
            UIManager.put("TextField.selectionForeground", fgBase);
        } else {
            UIManager.put("Panel.background", bgBase);
            UIManager.put("Label.foreground", fgBase);
            UIManager.put("CheckBox.background", bgBase);
            UIManager.put("CheckBox.foreground", fgBase);
            UIManager.put("ComboBox.background", Color.WHITE);
            UIManager.put("ComboBox.foreground", fgBase);
            UIManager.put("Spinner.background", Color.WHITE);
            UIManager.put("Spinner.foreground", fgBase);
            UIManager.put("FormattedTextField.background", Color.WHITE);
            UIManager.put("FormattedTextField.foreground", fgBase);
            UIManager.put("TabbedPane.background", bgBase);
            UIManager.put("TabbedPane.foreground", fgBase);
            UIManager.put("TabbedPane.selected", Color.WHITE);
            UIManager.put("TabbedPane.contentAreaColor", bgBase);
            UIManager.put("TabbedPane.opaque", true);
            UIManager.put("OptionPane.background", bgBase);
            UIManager.put("OptionPane.messageForeground", fgBase);
            UIManager.put("OptionPane.buttonAreaBackground", bgBase);
            UIManager.put("OptionPane.buttonForeground", fgBase);
            UIManager.put("OptionPane.foreground", fgBase);
            UIManager.put("Button.background", Color.WHITE);
            UIManager.put("Button.foreground", fgBase);
            UIManager.put("TextField.background", Color.WHITE);
            UIManager.put("TextField.foreground", fgBase);
            UIManager.put("TextField.caretForeground", fgBase);
            UIManager.put("TextField.selectionBackground", new Color(173, 214, 255));
            UIManager.put("TextField.selectionForeground", fgBase);
        }

        // atualiza a Árvore de Componentes com o novo UIManager
        SwingUtilities.updateComponentTreeUI(this);

        // pinta o fundo do JFrame
        getContentPane().setBackground(tabBg);

        // força as propriedades no componente JTabbedPane
        tabbedPane.setOpaque(true);
        tabbedPane.setBackground(tabBg);
        tabbedPane.setForeground(fgBase);

        // força a cor em cada aba já criada
        for (int i = 0; i < tabbedPane.getTabCount(); i++) {
            tabbedPane.setBackgroundAt(i, tabBg); // Isso pinta a aba em si
            tabbedPane.setForegroundAt(i, fgBase);

            // ajusta o componente customizado da aba
            java.awt.Component tabComp = tabbedPane.getTabComponentAt(i);
            if (tabComp instanceof JPanel p) {
                p.setOpaque(false); // Fica transparente para mostrar a cor que setamos na linha acima
                for (java.awt.Component child : p.getComponents()) {
                    if (child instanceof JLabel || child instanceof JButton) {
                        child.setForeground(fgBase);
                    }
                }
            }
        }

        // atualiza as áreas de texto do editor
        for (TabInfo tab : tabs) {
            applyThemeToPane(tab.textPane);
        }

        // restaura posição do cursor e seleção em cada aba (não perde ponto de inserção)
        for (int i = 0; i < tabs.size(); i++) {
            JTextPane pane = tabs.get(i).textPane;
            int maxLen = pane.getDocument().getLength();
            int cp = Math.min(caretPos[i], maxLen);
            int ss = Math.min(selStart[i], maxLen);
            int se = Math.min(selEnd[i], maxLen);
            try {
                pane.setCaretPosition(cp);
                pane.select(ss, se);
            } catch (IllegalArgumentException ignored) {
                // casos de seleção inválida após correções de documento
            }
        }

        // garante as cores corretas na Barra de Menus, Painel de Fontes e Barra de Status
        barraMenu.setOpaque(true);
        barraMenu.setBackground(bgBase);
        barraMenu.setBorder(javax.swing.BorderFactory.createEmptyBorder()); // Remove linha branca nativa

        statusBar.setBackground(tabBg);
        statusLabel.setForeground(fgBase);

        for (java.awt.Component comp : barraMenu.getComponents()) {
            if (comp instanceof JMenu) {
                comp.setForeground(fgBase);
                comp.setBackground(bgBase);
            } else if (comp instanceof JPanel p) {
                p.setOpaque(true); // Fundamental: obriga o painel a pintar seu fundo
                p.setBackground(bgBase);
                for (java.awt.Component child : p.getComponents()) {
                    child.setForeground(fgBase);
                    if (child instanceof JComboBox || child instanceof JSpinner || child instanceof JCheckBox) {
                        child.setBackground(inputBg);
                        if (child instanceof JCheckBox cb) {
                            cb.setOpaque(true); // Corrige fundo do checkbox
                        }
                    }
                }
            }
        }
    }

    private void applyThemeToPane(JTextPane pane) {
        Color bg = (currentTheme == Theme.ESCURO) ? new Color(40, 40, 40) : Color.WHITE;
        Color fg = (currentTheme == Theme.ESCURO) ? Color.WHITE : Color.BLACK;
        Color sel = (currentTheme == Theme.ESCURO) ? new Color(75, 110, 175) : new Color(173, 214, 255);

        pane.setBackground(bg);
        pane.setForeground(fg);
        pane.setCaretColor(fg);
        pane.setSelectionColor(sel);

        // Garante que o texto digitado após mudança de tema use a cor atual do tema
        javax.swing.text.MutableAttributeSet inputAttrs = pane.getInputAttributes();
        javax.swing.text.StyleConstants.setForeground(inputAttrs, fg);
        javax.swing.text.StyleConstants.setBackground(inputAttrs, bg);
        pane.setCharacterAttributes(inputAttrs, false);

        // Garante que textos já existentes não mantenham background em desarmonia do tema
        javax.swing.text.Document doc = pane.getDocument();
        if (doc instanceof javax.swing.text.StyledDocument styledDoc) {
            javax.swing.text.SimpleAttributeSet themeAttrs = new javax.swing.text.SimpleAttributeSet();
            javax.swing.text.StyleConstants.setForeground(themeAttrs, fg);
            javax.swing.text.StyleConstants.setBackground(themeAttrs, bg);
            styledDoc.setCharacterAttributes(0, doc.getLength(), themeAttrs, false);
        }
    }

    private void closeTab(int index) {
        if (index < 0 || index >= tabs.size()) return;

        TabInfo tab = tabs.get(index);
        if (tab.dirty) {
            int option = javax.swing.JOptionPane.showOptionDialog(
                this,
                "Há alterações não salvas. Deseja salvar antes de fechar?",
                "Confirmação",
                javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
                javax.swing.JOptionPane.WARNING_MESSAGE,
                null,
                new Object[] {"Salvar", "Não salvar", "Cancelar"},
                "Salvar"
            );
            if (option == javax.swing.JOptionPane.CANCEL_OPTION || option == javax.swing.JOptionPane.CLOSED_OPTION) {
                return;
            }
            if (option == javax.swing.JOptionPane.YES_OPTION) {
                tabbedPane.setSelectedIndex(index);
                salvarArquivo();
                if (tab.dirty) return;
            }
        }

        tabs.remove(index);
        tabbedPane.remove(index);

        if (tabs.isEmpty()) {
            criarNovaAba(null, null);
        }
    }

    public EditorUI() {
        // config janela
        setTitle(baseTitle);
        setSize(800, 600);
        setDefaultCloseOperation(DO_NOTHING_ON_CLOSE);
        setLocationRelativeTo(null);
        addWindowListener(new java.awt.event.WindowAdapter() {
            @Override
            public void windowClosing(java.awt.event.WindowEvent e) {
                if (confirmUnsavedChanges("sair")) {
                    dispose();
                    System.exit(0);
                }
            }
        });


        // fonte selecionável
        fontes = GraphicsEnvironment.getLocalGraphicsEnvironment().getAvailableFontFamilyNames();
        comboFontes = new JComboBox<>(fontes);
        comboFontes.setSelectedItem("Monospaced");

        spinnerTamanho = new JSpinner(new SpinnerNumberModel(14, 6, 72, 1));

        cbNegrito = new JCheckBox("Negrito");
        cbItalico = new JCheckBox("Itálico");

        ActionListener estiloListener = e -> atualizarFonte();
        comboFontes.addActionListener(estiloListener);
        cbNegrito.addActionListener(estiloListener);
        cbItalico.addActionListener(estiloListener);
        spinnerTamanho.addChangeListener(e -> atualizarFonte());

        // abas de edição (documentos)
        tabbedPane = new JTabbedPane();
        tabbedPane.addChangeListener(e -> atualizarTitulo());

        setLayout(new BorderLayout());
        add(tabbedPane, BorderLayout.CENTER);

        statusBar = new JPanel(new FlowLayout(FlowLayout.LEFT, 8, 3));
        statusLabel = new JLabel();
        statusBar.add(statusLabel);
        add(statusBar, BorderLayout.SOUTH);

        criarNovaAba(null, null);

        // barra de menus
        barraMenu = new JMenuBar();
        menuArquivo = new JMenu("Arquivo");

        itemNovo = new JMenuItem("Novo");
        itemAbrir = new JMenuItem("Abrir");
        itemSalvar = new JMenuItem("Salvar");
        itemSair = new JMenuItem("Sair");

        menuArquivo.add(itemNovo);
        menuArquivo.add(itemAbrir);
        menuArquivo.add(itemSalvar);
        menuArquivo.addSeparator();
        menuArquivo.add(itemSair);

        barraMenu.add(menuArquivo);

        // menu Editar / Busca
        menuEditar = new JMenu("Editar");
        itemBuscar = new JMenuItem("Buscar");
        itemBuscar.setAccelerator(KeyStroke.getKeyStroke(KeyEvent.VK_F, InputEvent.CTRL_DOWN_MASK));
        menuEditar.add(itemBuscar);
        barraMenu.add(menuEditar);

        // menu Tema
        menuTema = new JMenu("Tema");
        itemTemaClaro = new JRadioButtonMenuItem("Claro");
        itemTemaEscuro = new JRadioButtonMenuItem("Escuro");
        javax.swing.ButtonGroup temaGroup = new javax.swing.ButtonGroup();
        temaGroup.add(itemTemaClaro);
        temaGroup.add(itemTemaEscuro);
        itemTemaClaro.setSelected(currentTheme == Theme.CLARO);
        itemTemaEscuro.setSelected(currentTheme == Theme.ESCURO);
        itemTemaClaro.addActionListener(e -> applyTheme(Theme.CLARO));
        itemTemaEscuro.addActionListener(e -> applyTheme(Theme.ESCURO));
        menuTema.add(itemTemaClaro);
        menuTema.add(itemTemaEscuro);
        barraMenu.add(menuTema);

        // alinha seletor de fontes ao lado direito do menu
        barraMenu.add(Box.createHorizontalGlue());
        JPanel painelFonte = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        painelFonte.setOpaque(false);
        painelFonte.add(new JLabel("Fonte:"));
        painelFonte.add(comboFontes);
        painelFonte.add(new JLabel("Tamanho:"));
        painelFonte.add(spinnerTamanho);
        painelFonte.add(cbNegrito);
        painelFonte.add(cbItalico);
        barraMenu.add(painelFonte);

        setJMenuBar(barraMenu);

        applyTheme(currentTheme);
    }

    private void atualizarFonte() {
        JTextPane pane = getCurrentTextPane();
        if (pane == null) return;

        String fonte = (String) comboFontes.getSelectedItem();
        if (fonte == null) return;

        int tamanho = ((Number) spinnerTamanho.getValue()).intValue();

        javax.swing.text.SimpleAttributeSet attrs = new javax.swing.text.SimpleAttributeSet();
        javax.swing.text.StyleConstants.setFontFamily(attrs, fonte);
        javax.swing.text.StyleConstants.setBold(attrs, cbNegrito.isSelected());
        javax.swing.text.StyleConstants.setItalic(attrs, cbItalico.isSelected());
        javax.swing.text.StyleConstants.setFontSize(attrs, tamanho);

        int start = pane.getSelectionStart();
        int end = pane.getSelectionEnd();
        if (start != end) {
            pane.getStyledDocument().setCharacterAttributes(start, end - start, attrs, false);
        } else {
            pane.setCharacterAttributes(attrs, false);
        }
    }

    private void configureUndoRedo(TabInfo tab) {
        JTextPane pane = tab.textPane;
        pane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Z, InputEvent.CTRL_DOWN_MASK), "undo");
        pane.getInputMap().put(KeyStroke.getKeyStroke(KeyEvent.VK_Y, InputEvent.CTRL_DOWN_MASK), "redo");
        pane.getActionMap().put("undo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performUndo(tab);
            }
        });
        pane.getActionMap().put("redo", new AbstractAction() {
            @Override
            public void actionPerformed(ActionEvent e) {
                performRedo(tab);
            }
        });
    }

    private void performUndo(TabInfo tab) {
        if (tab == null || !tab.undoManager.canUndo()) return;
        try {
            tab.undoManager.undo();
        } catch (CannotUndoException ex) {
            // ignora
        }
    }

    private void performRedo(TabInfo tab) {
        if (tab == null || !tab.undoManager.canRedo()) return;
        try {
            tab.undoManager.redo();
        } catch (CannotRedoException ex) {
            // ignora
        }
    }

    private void attachDocumentListeners(TabInfo tab) {
        Document doc = tab.textPane.getDocument();
        if (doc == null) return;

        doc.addDocumentListener(new DocumentListener() {
            @Override public void insertUpdate(DocumentEvent e) { handleInsert(tab, e); updateStatusBar(); }
            @Override public void removeUpdate(DocumentEvent e) { markDirty(tab, true); updateStatusBar(); }
            @Override public void changedUpdate(DocumentEvent e) { /* não usado */ }
        });

        doc.addUndoableEditListener(e -> tab.undoManager.addEdit(e.getEdit()));
        configureUndoRedo(tab);
    }

    private void criarNovaAba(String fileName, Document document) {
        JTextPane pane = new WrappingTextPane();
        pane.setFont(new Font("Monospaced", Font.PLAIN, 14));
        applyThemeToPane(pane);
        if (document != null) {
            pane.setDocument(document);
        }
        // mantém tema aplicado nas novas abas
        Color bg = (currentTheme == Theme.ESCURO) ? new Color(40, 40, 40) : Color.WHITE;
        Color fg = (currentTheme == Theme.ESCURO) ? Color.WHITE : Color.BLACK;
        Color sel = (currentTheme == Theme.ESCURO) ? new Color(75, 110, 175) : new Color(173, 214, 255);
        pane.setBackground(bg);
        pane.setForeground(fg);
        pane.setCaretColor(fg);
        pane.setSelectionColor(sel);

        TabInfo tab = new TabInfo(pane, fileName);
        tabs.add(tab);
        attachDocumentListeners(tab);

        JScrollPane scrollPane = new JScrollPane(pane, JScrollPane.VERTICAL_SCROLLBAR_AS_NEEDED, JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
        tabbedPane.addTab(null, scrollPane);
        int idx = tabbedPane.getTabCount() - 1;
        tabbedPane.setTabComponentAt(idx, createTabComponent(tab));
        tabbedPane.setSelectedIndex(idx);
        atualizarTitulo();
    }

    private void handleInsert(TabInfo tab, DocumentEvent e) {
        if (isAutoCorrigindo) return;
        markDirty(tab, true);
        try {
            int offset = e.getOffset();
            int length = e.getLength();
            Document doc = e.getDocument();
            String inserted = doc.getText(offset, length);
            for (int i = 0; i < inserted.length(); i++) {
                char ch = inserted.charAt(i);
                if (!Character.isLetter(ch)) {
                    int caretPos = offset + i + 1;
                    // agendar correção para depois de terminar a notificação do documento
                    SwingUtilities.invokeLater(() -> autocorrecaoAntesDoCaret(tab, caretPos));
                }
            }
        } catch (BadLocationException ex) {
            // ignora
        }
    }

    private void autocorrecaoAntesDoCaret(TabInfo tab, int caretPos) {
        try {
            JTextPane pane = tab.textPane;
            Document doc = pane.getDocument();
            int end = caretPos - 1;

            // move para o último caractere de palavra antes do delimitador
            while (end >= 0 && !Character.isLetter(doc.getText(end, 1).charAt(0))) {
                end--;
            }
            if (end < 0) return;

            int start = end;
            while (start > 0 && Character.isLetter(doc.getText(start - 1, 1).charAt(0))) {
                start--;
            }

            int wordLen = end - start + 1;
            if (wordLen <= 0) return;

            String word = doc.getText(start, wordLen);
            String wordLower = word.toLowerCase(Locale.ROOT);

            // evita re-autocorreção imediata da mesma palavra no mesmo local
            if (skipNextReCorrection && start == lastCorrectionStart && wordLower.equals(lastCorrectedWordLower)) {
                skipNextReCorrection = false;
                return;
            }

            if (corretor.isPalavraValida(word)) return;

            String correcao = corretor.sugerirCorrecao(wordLower);
            if (correcao == null) return;

            String corrigido = corretor.aplicarCapitalizacao(word, correcao);

            // preserva a formatação das palavaras auto-corrigidas
            javax.swing.text.StyledDocument styledDoc = null;
            javax.swing.text.AttributeSet attrs = null;
            if (doc instanceof javax.swing.text.StyledDocument s) {
                styledDoc = s;
                attrs = styledDoc.getCharacterElement(start).getAttributes();
            }

            isAutoCorrigindo = true;
            doc.remove(start, wordLen);
            if (styledDoc != null && attrs != null) {
                styledDoc.insertString(start, corrigido, attrs);
            } else {
                doc.insertString(start, corrigido, null);
            }

            int newCaret = start + corrigido.length() + (caretPos - end - 1);
            pane.setCaretPosition(Math.min(newCaret, doc.getLength()));

            // permite palavras inexistentes propositalmente caso o usuário digite novamente
            lastCorrectionStart = start;
            lastCorrectedWordLower = wordLower;
            skipNextReCorrection = true;
        } catch (BadLocationException ex) {
            // ignora
        } finally {
            isAutoCorrigindo = false;
        }
    }

    private TabInfo getCurrentTab() {
        int idx = tabbedPane.getSelectedIndex();
        if (idx < 0 || idx >= tabs.size()) return null;
        return tabs.get(idx);
    }

    private JTextPane getCurrentTextPane() {
        TabInfo tab = getCurrentTab();
        return (tab != null) ? tab.textPane : null;
    }

    private void markDirty(TabInfo tab, boolean dirty) {
        if (tab == null) return;
        if (tab.dirty == dirty) return;
        tab.dirty = dirty;
        updateTabTitle(tab);
        atualizarTitulo();
    }

    private void atualizarTitulo() {
        String titulo = baseTitle;
        TabInfo tab = getCurrentTab();
        if (tab != null) {
            titulo += " - " + tab.getDisplayName();
        }
        setTitle(titulo);
        updateStatusBar();
    }

    private void updateStatusBar() {
        JTextPane pane = getCurrentTextPane();
        if (pane == null) {
            statusLabel.setText("Linhas: 0 | Caracteres: 0");
            return;
        }

        String texto = pane.getText();
        int charCount = texto.length();
        int lineCount = pane.getDocument().getDefaultRootElement().getElementCount();
        statusLabel.setText("Linhas: " + lineCount + " | Caracteres: " + charCount);
    }

    private boolean confirmUnsavedChanges(String action) {
        TabInfo tab = getCurrentTab();
        if (tab == null || !tab.dirty) return true;

        int option = javax.swing.JOptionPane.showOptionDialog(
            this,
            "Há alterações não salvas. Deseja salvar antes de " + action + "?",
            "Confirmação",
            javax.swing.JOptionPane.YES_NO_CANCEL_OPTION,
            javax.swing.JOptionPane.WARNING_MESSAGE,
            null,
            new Object[] {"Salvar", "Não salvar", "Cancelar"},
            "Salvar"
        );

        if (option == javax.swing.JOptionPane.CANCEL_OPTION || option == javax.swing.JOptionPane.CLOSED_OPTION) {
            return false;
        }

        if (option == javax.swing.JOptionPane.YES_OPTION) {
            salvarArquivo();
            tab = getCurrentTab();
            return tab == null || !tab.dirty;
        }

        // "Não salvar" selecionado
        return true;
    }

    // ouvintes registrados após construção para maior segurança
    public void initListeners() {
        itemNovo.addActionListener(this);
        itemAbrir.addActionListener(this);
        itemSalvar.addActionListener(this);
        itemSair.addActionListener(this);
        itemBuscar.addActionListener(this);
    }

    @Override
    public void actionPerformed(ActionEvent e) {
        if (e.getSource() == itemNovo) {
            if (!confirmUnsavedChanges("criar novo arquivo")) return;
            criarNovaAba(null, null);
        } else if (e.getSource() == itemAbrir) {
            if (!confirmUnsavedChanges("abrir outro arquivo")) return;
            abrirArquivo();
        } else if (e.getSource() == itemSalvar) {
            salvarArquivo();
        } else if (e.getSource() == itemBuscar) {
            buscarPalavra();
        } else if (e.getSource() == itemSair) {
            if (!confirmUnsavedChanges("sair")) return;
            System.exit(0);
        }
    }

    private void abrirArquivo() {
        try {
            GerenciadorArquivos.ResultadoAbertura result = gi.abrirArquivo(this);
            if (result == null) return;

            criarNovaAba(result.fileName, result.document);
        } catch (IOException | BadLocationException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Erro ao abrir arquivo.");
        }
    }

    private void salvarArquivo() {
        try {
            TabInfo tab = getCurrentTab();
            if (tab == null) return;

            String fileName = gi.salvarArquivo(this, tab.textPane.getDocument());
            if (fileName == null) return;

            tab.fileName = fileName;
            markDirty(tab, false);
            updateTabTitle(tab);
            atualizarTitulo();
        } catch (IOException | BadLocationException ex) {
            javax.swing.JOptionPane.showMessageDialog(this, "Erro ao salvar arquivo.");
        }
    }

    private void buscarPalavra() {
        TabInfo tab = getCurrentTab();
        if (tab == null) return;

        String termo = javax.swing.JOptionPane.showInputDialog(this, "Buscar:", lastSearchTerm);
        if (termo == null || termo.isEmpty()) return;

        // Se for a mesma busca repetida, segue para a próxima ocorrência
        if (termo.equals(lastSearchTerm) && lastSearchIndex >= 0) {
            int nextStart = lastSearchIndex + termo.length();
            lastSearchTerm = termo;
            if (!buscarTermo(termo, nextStart)) {
                // ao não encontrar, tenta desde o início
                if (!buscarTermo(termo, 0)) {
                    javax.swing.JOptionPane.showMessageDialog(this, "Termo não encontrado.");
                } else {
                    javax.swing.JOptionPane.showMessageDialog(this, "Reiniciando busca do início do documento.");
                }
            }
            return;
        }

        // nova busca: começa da posição atual do cursor/seleção
        lastSearchTerm = termo;
        lastSearchIndex = -1;

        JTextPane pane = tab.textPane;
        int start = pane.getSelectionEnd();
        if (start <= 0) {
            start = pane.getCaretPosition();
        }

        if (!buscarTermo(termo, start)) {
            // ao não encontrar, tenta desde o início
            if (!buscarTermo(termo, 0)) {
                javax.swing.JOptionPane.showMessageDialog(this, "Termo não encontrado.");
            } else {
                javax.swing.JOptionPane.showMessageDialog(this, "Reiniciando busca do início do documento.");
            }
        }
    }

    private boolean buscarTermo(String termo, int inicio) {
        JTextPane pane = getCurrentTextPane();
        if (pane == null) return false;

        String texto = pane.getText();
        String textoLower = texto.toLowerCase(Locale.ROOT);
        String termoLower = termo.toLowerCase(Locale.ROOT);

        int idx = textoLower.indexOf(termoLower, Math.max(0, inicio));
        if (idx < 0) return false;

        lastSearchIndex = idx;
        pane.requestFocusInWindow();
        pane.select(idx, idx + termo.length());
        return true;
    }
}