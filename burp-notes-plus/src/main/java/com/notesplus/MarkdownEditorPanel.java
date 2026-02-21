package com.notesplus;

import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.autolink.AutolinkExtension;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.awt.event.*;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public class MarkdownEditorPanel {

    private final JPanel container;
    private final JPanel linesPanel;
    private final List<LineRow> rows = new ArrayList<>();
    private final Consumer<String> onContentChanged;
    private boolean updatingContent = false;

    private static final Parser MD_PARSER;
    private static final HtmlRenderer MD_RENDERER;

    static {
        MutableDataSet opts = new MutableDataSet();
        opts.set(Parser.EXTENSIONS, Arrays.asList(
                TablesExtension.create(),
                StrikethroughExtension.create(),
                TaskListExtension.create(),
                AutolinkExtension.create()));
        MD_PARSER = Parser.builder(opts).build();
        MD_RENDERER = HtmlRenderer.builder(opts).build();
    }

    public MarkdownEditorPanel(Consumer<String> onContentChanged) {
        this.onContentChanged = onContentChanged;

        linesPanel = new JPanel();
        linesPanel.setLayout(new BoxLayout(linesPanel, BoxLayout.Y_AXIS));
        linesPanel.setBackground(Color.WHITE);
        linesPanel.setBorder(new EmptyBorder(16, 20, 16, 20));

        linesPanel.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseClicked(MouseEvent e) {
                if (e.getClickCount() == 2 && e.getSource() == linesPanel) {
                    if (rows.isEmpty()) {
                        addRow("", true);
                    } else {
                        rows.get(rows.size() - 1).startEditing();
                    }
                }
            }
        });

        container = new JPanel(new BorderLayout());
        container.setBackground(Color.WHITE);
        container.add(linesPanel, BorderLayout.NORTH);
        container.add(Box.createVerticalGlue(), BorderLayout.CENTER);

        // Auto-create and focus first row when loaded
        SwingUtilities.invokeLater(this::requestFocus);
    }

    public JComponent getComponent() {
        return container;
    }

    public void requestFocus() {
        SwingUtilities.invokeLater(() -> {
            if (rows.isEmpty()) {
                addRow("", true);
            } else {
                rows.get(0).startEditing();
            }
        });
    }

    public void setContent(String markdown) {
        updatingContent = true;
        rows.clear();
        linesPanel.removeAll();

        String[] lines = markdown.isEmpty() ? new String[]{""} : markdown.split("\n", -1);
        for (String line : lines) {
            addRow(line, false);
        }

        linesPanel.revalidate();
        linesPanel.repaint();
        updatingContent = false;
    }

    public String getContent() {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < rows.size(); i++) {
            sb.append(rows.get(i).getRawText());
            if (i < rows.size() - 1) {
                sb.append("\n");
            }
        }
        return sb.toString();
    }

    private LineRow addRow(String rawText, boolean startEditing) {
        LineRow row = new LineRow(rawText);
        rows.add(row);
        linesPanel.add(row.panel);
        if (startEditing) {
            SwingUtilities.invokeLater(row::startEditing);
        }
        return row;
    }

    private LineRow insertRowAfter(int index, String rawText) {
        LineRow row = new LineRow(rawText);
        rows.add(index + 1, row);
        linesPanel.add(row.panel, index + 1);
        linesPanel.revalidate();
        linesPanel.repaint();
        return row;
    }

    private void removeRow(int index) {
        if (rows.size() <= 1) {
            return;
        }
        LineRow row = rows.remove(index);
        linesPanel.remove(row.panel);
        linesPanel.revalidate();
        linesPanel.repaint();
    }

    private void notifyContentChanged() {
        if (!updatingContent) {
            onContentChanged.accept(getContent());
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Markdown rendering helpers
    // ─────────────────────────────────────────────────────────────────────────
    private static String renderMarkdownLine(String raw) {
        String md = raw.isBlank() ? " " : raw;
        Node doc = MD_PARSER.parse(md);
        String html = MD_RENDERER.render(doc).trim();
        if (html.startsWith("<p>") && html.endsWith("</p>") && !html.contains("\n")) {
            html = html.substring(3, html.length() - 4);
        }
        if (html.isEmpty() || html.equals("&nbsp;")) {
            return "<span style='display: inline-block; min-height: 1em;'>&nbsp;</span>";
        }
        return html;
    }

    private static String escapeHtml(String text) {
        return text.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    private static String buildFullHtml(String bodyHtml) {
        return "<html><head><style>"
                + "body { font-family: 'Segoe UI', Arial, sans-serif; font-size: 14px; "
                + "color: #333333; margin: 0; padding: 0; background: transparent; }"
                + "h1 { font-size: 22px; color: #0056b3; margin: 2px 0; font-weight: 700; }"
                + "h2 { font-size: 19px; color: #0056b3; margin: 2px 0; font-weight: 600; }"
                + "h3 { font-size: 16px; color: #0056b3; margin: 2px 0; }"
                + "h4,h5,h6 { color: #0056b3; margin: 2px 0; }"
                + "strong { color: #000000; font-weight: 700; }"
                + "em { color: #555555; font-style: italic; }"
                + "del { color: #888; }"
                + "blockquote { background: #DC143C; color: #ffffff; margin: 0; padding: 8px 12px; border-radius: 4px; font-style: italic; }"
                + "ul, ol { margin: 0; padding-left: 18px; }"
                + "a { color: #0366d6; }"
                + "hr { border: none; border-top: 1px solid #ddd; }"
                + "table { border-collapse: collapse; } "
                + "td, th { border: 1px solid #ddd; padding: 2px 8px; }"
                + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LineRow
    // ─────────────────────────────────────────────────────────────────────────
    private class LineRow {

        final JPanel panel;
        private String rawText;
        private final JEditorPane renderedPane;
        private final JTextField editField;
        private boolean editing = false;

        int rowIndex() {
            return rows.indexOf(this);
        }

        LineRow(String initialText) {
            this.rawText = initialText;

            panel = new JPanel(new CardLayout());
            panel.setBackground(Color.WHITE);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            panel.setMinimumSize(new Dimension(0, 28));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            renderedPane = new JEditorPane();
            renderedPane.setContentType("text/html");
            renderedPane.setEditable(false);
            renderedPane.setOpaque(false);
            renderedPane.setBackground(Color.WHITE);
            renderedPane.setBorder(new EmptyBorder(3, 0, 3, 0));
            renderedPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            renderedPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            renderedPane.addPropertyChangeListener("page", evt -> SwingUtilities.invokeLater(() -> {
                int h = renderedPane.getPreferredSize().height;
                panel.setPreferredSize(new Dimension(0, Math.max(28, h)));
                linesPanel.revalidate();
                linesPanel.repaint();
            }));

            renderedPane.addMouseListener(new MouseAdapter() {
                @Override
                public void mouseEntered(MouseEvent e) {
                    panel.setBackground(new Color(245, 248, 255));
                    renderedPane.setBackground(new Color(245, 248, 255));
                }

                @Override
                public void mouseExited(MouseEvent e) {
                    panel.setBackground(Color.WHITE);
                    renderedPane.setBackground(Color.WHITE);
                }

                @Override
                public void mouseClicked(MouseEvent e) {
                    if (e.getClickCount() == 2) {
                        startEditing();
                    }
                }
            });

            updateRendered();

            editField = new JTextField(rawText);
            editField.setBackground(new Color(250, 250, 255));
            editField.setForeground(new Color(50, 50, 50));
            editField.setCaretColor(new Color(0, 100, 250));
            editField.setFont(new Font("Consolas", Font.PLAIN, 14));
            editField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(100, 150, 255)),
                    new EmptyBorder(3, 6, 3, 0)));
            editField.setMaximumSize(new Dimension(Integer.MAX_VALUE, 32));

            editField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    stopEditing();
                }
            });

            editField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int idx = rowIndex();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        stopEditing();
                        LineRow newRow = insertRowAfter(idx, "");
                        SwingUtilities.invokeLater(newRow::startEditing);
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE
                            && editField.getText().isEmpty() && rows.size() > 1) {
                        stopEditing();
                        removeRow(idx);
                        int prev = Math.max(0, idx - 1);
                        if (prev < rows.size()) {
                            rows.get(prev).startEditing();
                            rows.get(prev).editField.setCaretPosition(
                                    rows.get(prev).editField.getText().length());
                        }
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_UP && idx > 0) {
                        stopEditing();
                        rows.get(idx - 1).startEditing();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_DOWN && idx < rows.size() - 1) {
                        stopEditing();
                        rows.get(idx + 1).startEditing();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopEditing();
                        e.consume();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    rawText = editField.getText();
                    notifyContentChanged();
                }
            });

            panel.add(renderedPane, "RENDERED");
            panel.add(editField, "EDIT");

            showCard("RENDERED");
        }

        void startEditing() {
            if (editing) {
                return;
            }
            editing = true;
            editField.setText(rawText);
            showCard("EDIT");
            editField.requestFocusInWindow();
            SwingUtilities.invokeLater(() -> editField.setCaretPosition(editField.getText().length()));
        }

        void stopEditing() {
            if (!editing) {
                return;
            }
            editing = false;
            rawText = editField.getText();
            updateRendered();
            showCard("RENDERED");
            notifyContentChanged();
        }

        String getRawText() {
            if (editing) {
                return editField.getText();
            }
            return rawText;
        }

        private void updateRendered() {
            String bodyHtml = renderMarkdownLine(rawText);
            String html = buildFullHtml(bodyHtml);
            renderedPane.setText(html);
            SwingUtilities.invokeLater(() -> {
                int h = renderedPane.getPreferredSize().height;
                panel.setPreferredSize(new Dimension(0, Math.max(28, h)));
                linesPanel.revalidate();
                linesPanel.repaint();
            });
        }

        private void showCard(String card) {
            ((CardLayout) panel.getLayout()).show(panel, card);
        }
    }
}
