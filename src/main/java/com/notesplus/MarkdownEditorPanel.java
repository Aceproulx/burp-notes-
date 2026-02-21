package com.notesplus;

import java.awt.BorderLayout;
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.event.FocusAdapter;
import java.awt.event.FocusEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JEditorPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.SwingUtilities;
import javax.swing.border.EmptyBorder;

import com.vladsch.flexmark.ext.autolink.AutolinkExtension;
import com.vladsch.flexmark.ext.gfm.strikethrough.StrikethroughExtension;
import com.vladsch.flexmark.ext.gfm.tasklist.TaskListExtension;
import com.vladsch.flexmark.ext.tables.TablesExtension;
import com.vladsch.flexmark.html.HtmlRenderer;
import com.vladsch.flexmark.parser.Parser;
import com.vladsch.flexmark.util.ast.Node;
import com.vladsch.flexmark.util.data.MutableDataSet;

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
                if (e.getClickCount() == 1 && e.getSource() == linesPanel) {
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

        List<String> logicalLines = new ArrayList<>();
        String[] lines = markdown.isEmpty() ? new String[] { "" } : markdown.split("\n", -1);

        StringBuilder codeBlock = null;
        for (String line : lines) {
            if (line.trim().startsWith("```")) {
                if (codeBlock == null) {
                    // Start code block
                    codeBlock = new StringBuilder(line);
                } else {
                    // End code block
                    codeBlock.append("\n").append(line);
                    logicalLines.add(codeBlock.toString());
                    codeBlock = null;
                }
            } else if (codeBlock != null) {
                // Inside code block
                codeBlock.append("\n").append(line);
            } else {
                // Regular line
                logicalLines.add(line);
            }
        }

        // Handle unclosed code blocks
        if (codeBlock != null) {
            logicalLines.add(codeBlock.toString());
        }

        for (String logicalLine : logicalLines) {
            addRow(logicalLine, false);
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
        // Remove wrapping <p> tags only if this is a simple inline element
        if (html.startsWith("<p>") && html.endsWith("</p>")) {
            String inner = html.substring(3, html.length() - 4).trim();
            // Keep paragraphs for complex content, remove for simple inline
            if (!inner.contains("<h") && !inner.contains("<ul") && !inner.contains("<ol")
                    && !inner.contains("<table") && !inner.contains("<blockquote")
                    && !inner.matches(".*<[^>]+>.*<[^>]+>.*")) {
                html = inner;
            }
        }
        if (html.isEmpty() || html.equals("&nbsp;")) {
            return "<span style='display: inline-block; min-height: 1.2em;'>&nbsp;</span>";
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
                + "body { "
                + "  font-family: -apple-system, BlinkMacSystemFont, 'Segoe UI', 'Helvetica Neue', Arial, sans-serif; "
                + "  font-size: 14px; "
                + "  color: #3d3d3d; "
                + "  margin: 0; "
                + "  padding: 0; "
                + "  background: transparent; "
                + "  line-height: 1.6; "
                + "} "
                + "h1, h2, h3, h4, h5, h6 { "
                + "  color: #0056b3; "
                + "  margin: 4px 0; "
                + "  font-weight: 600; "
                + "  line-height: 1.3; "
                + "} "
                + "h1 { font-size: 24px; } "
                + "h2 { font-size: 20px; } "
                + "h3 { font-size: 18px; } "
                + "h4 { font-size: 16px; } "
                + "h5 { font-size: 15px; } "
                + "h6 { font-size: 14px; } "
                + "p { margin: 2px 0; } "
                + "strong, b { "
                + "  color: #000000; "
                + "  font-weight: 700; "
                + "} "
                + "em, i { "
                + "  color: #555555; "
                + "  font-style: italic; "
                + "} "
                + "code { "
                + "  background-color: #f6f8fa; "
                + "  border: 1px solid #eaeaea; "
                + "  border-radius: 3px; "
                + "  padding: 2px 6px; "
                + "  font-family: 'Consolas', 'Monaco', 'Courier New', monospace; "
                + "  font-size: 13px; "
                + "  color: #333333; "
                + "} "
                + "pre { "
                + "  background-color: #f6f8fa; "
                + "  border: 1px solid #eaeaea; "
                + "  border-radius: 3px; "
                + "  padding: 8px 12px; "
                + "  overflow: auto; "
                + "  font-family: 'Consolas', 'Monaco', 'Courier New', monospace; "
                + "  font-size: 12px; "
                + "  color: #333333; "
                + "  margin: 4px 0; "
                + "  word-break: break-all; "
                + "} "
                + "pre code { "
                + "  background: transparent; "
                + "  border: none; "
                + "  padding: 0; "
                + "} "
                + "del { "
                + "  color: #888; "
                + "  text-decoration: line-through; "
                + "} "
                + "strike { "
                + "  color: #888; "
                + "  text-decoration: line-through; "
                + "} "
                + "mark { "
                + "  background-color: #fff3cd; "
                + "  color: #333; "
                + "  padding: 2px 4px; "
                + "} "
                + "blockquote { "
                + "  background: #f6f6f6; "
                + "  border-left: 4px solid #DC143C; "
                + "  color: #555555; "
                + "  margin: 4px 0; "
                + "  padding: 8px 12px; "
                + "  border-radius: 0; "
                + "  font-style: normal; "
                + "} "
                + "ul, ol { "
                + "  margin: 2px 0; "
                + "  padding-left: 24px; "
                + "} "
                + "li { "
                + "  margin: 2px 0; "
                + "  line-height: 1.5; "
                + "} "
                + "a { "
                + "  color: #0366d6; "
                + "  text-decoration: none; "
                + "} "
                + "a:hover { "
                + "  text-decoration: underline; "
                + "} "
                + "hr { "
                + "  border: none; "
                + "  border-top: 1px solid #ddd; "
                + "  margin: 8px 0; "
                + "} "
                + "table { "
                + "  border-collapse: collapse; "
                + "  margin: 4px 0; "
                + "  font-size: 13px; "
                + "} "
                + "td, th { "
                + "  border: 1px solid #ddd; "
                + "  padding: 6px 13px; "
                + "  text-align: left; "
                + "} "
                + "th { "
                + "  background-color: #f6f8fa; "
                + "  font-weight: 600; "
                + "} "
                + "tr:nth-child(2n) { "
                + "  background-color: #f8f8f8; "
                + "} "
                + "img { "
                + "  max-width: 100%; "
                + "  height: auto; "
                + "} "
                + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // LineRow
    // ─────────────────────────────────────────────────────────────────────────
    private class LineRow {

        final JPanel panel;
        private String rawText;
        private final JEditorPane renderedPane;
        private final JTextField singleLineEditField;
        private final JTextArea multiLineEditField;
        private final JScrollPane multiLineScroll;
        private boolean editing = false;
        private boolean useMultiLine = false;

        int rowIndex() {
            return rows.indexOf(this);
        }

        LineRow(String initialText) {
            this.rawText = initialText;
            this.useMultiLine = initialText.trim().startsWith("```");

            panel = new JPanel(new CardLayout());
            panel.setBackground(Color.WHITE);
            panel.setMaximumSize(new Dimension(Integer.MAX_VALUE, Integer.MAX_VALUE));
            panel.setMinimumSize(new Dimension(0, 28));
            panel.setAlignmentX(Component.LEFT_ALIGNMENT);

            renderedPane = new JEditorPane();
            renderedPane.setContentType("text/html;charset=utf-8");
            renderedPane.setEditable(false);
            renderedPane.setOpaque(true);
            renderedPane.setBackground(Color.WHITE);
            renderedPane.setBorder(new EmptyBorder(3, 6, 3, 6));
            renderedPane.putClientProperty(JEditorPane.HONOR_DISPLAY_PROPERTIES, true);
            renderedPane.setCursor(Cursor.getPredefinedCursor(Cursor.TEXT_CURSOR));
            // Enable text selection and right-click copy
            renderedPane.setFocusable(true);
            renderedPane.addPropertyChangeListener("page", evt -> SwingUtilities.invokeLater(() -> {
                Dimension prefSize = renderedPane.getPreferredSize();
                int h = Math.max(28, prefSize.height);
                panel.setPreferredSize(new Dimension(0, h));
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
                    if (e.getClickCount() == 1) {
                        startEditing();
                    }
                }
            });

            // Add keyboard shortcuts for rendered pane (Ctrl+A for select all, Ctrl+C for
            // copy)
            renderedPane.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    if (e.isControlDown() && e.getKeyCode() == KeyEvent.VK_A) {
                        renderedPane.selectAll();
                        e.consume();
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        // Allow escape to deselect and switch context
                        renderedPane.setCaretPosition(0);
                    }
                }
            });

            updateRendered();

            // Single-line editor (JTextField)
            singleLineEditField = new JTextField(rawText);
            singleLineEditField.setBackground(new Color(250, 250, 255));
            singleLineEditField.setForeground(new Color(50, 50, 50));
            singleLineEditField.setCaretColor(new Color(0, 100, 250));
            singleLineEditField.setFont(new Font("Consolas", Font.PLAIN, 14));
            singleLineEditField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(100, 150, 255)),
                    new EmptyBorder(3, 6, 3, 6)));

            singleLineEditField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    stopEditing();
                }
            });

            singleLineEditField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int idx = rowIndex();
                    if (e.getKeyCode() == KeyEvent.VK_ENTER) {
                        e.consume();
                        String currentText = singleLineEditField.getText();
                        int caretPos = singleLineEditField.getCaretPosition();
                        String beforeCursor = currentText.substring(0, caretPos);
                        String afterCursor = currentText.substring(caretPos);

                        singleLineEditField.setText(beforeCursor);
                        rawText = beforeCursor;

                        LineRow newRow = insertRowAfter(idx, afterCursor);
                        stopEditing();
                        SwingUtilities.invokeLater(() -> {
                            newRow.startEditing();
                            if (newRow.useMultiLine) {
                                newRow.multiLineEditField.setCaretPosition(0);
                            } else {
                                newRow.singleLineEditField.setCaretPosition(0);
                            }
                        });
                    } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE
                            && singleLineEditField.getText().isEmpty() && idx > 0) {
                        e.consume();
                        int prevIdx = idx - 1;
                        String prevText = rows.get(prevIdx).getRawText();
                        stopEditing();
                        removeRow(idx);
                        rows.get(prevIdx).startEditing();
                        LineRow prevRow = rows.get(prevIdx);
                        if (prevRow.useMultiLine) {
                            prevRow.multiLineEditField.setCaretPosition(prevText.length());
                        } else {
                            prevRow.singleLineEditField.setCaretPosition(prevText.length());
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopEditing();
                        e.consume();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    rawText = singleLineEditField.getText();
                    if (rawText.trim().startsWith("```")) {
                        useMultiLine = true;
                        switchToMultiLine();
                    }
                    notifyContentChanged();
                }
            });

            // Multi-line editor (JTextArea)
            multiLineEditField = new JTextArea(rawText);
            multiLineEditField.setLineWrap(true);
            multiLineEditField.setWrapStyleWord(true);
            multiLineEditField.setBackground(new Color(250, 250, 255));
            multiLineEditField.setForeground(new Color(50, 50, 50));
            multiLineEditField.setCaretColor(new Color(0, 100, 250));
            multiLineEditField.setFont(new Font("Consolas", Font.PLAIN, 14));
            multiLineEditField.setBorder(BorderFactory.createCompoundBorder(
                    BorderFactory.createMatteBorder(0, 2, 0, 0, new Color(100, 150, 255)),
                    new EmptyBorder(3, 6, 3, 6)));

            multiLineScroll = new JScrollPane(multiLineEditField);
            multiLineScroll.setBorder(null);
            multiLineScroll.setBackground(new Color(250, 250, 255));
            multiLineScroll.setHorizontalScrollBarPolicy(JScrollPane.HORIZONTAL_SCROLLBAR_NEVER);
            multiLineScroll.setVerticalScrollBarPolicy(JScrollPane.VERTICAL_SCROLLBAR_NEVER);
            multiLineScroll.getViewport().setBackground(new Color(250, 250, 255));

            multiLineEditField.addFocusListener(new FocusAdapter() {
                @Override
                public void focusLost(FocusEvent e) {
                    stopEditing();
                }
            });

            multiLineEditField.addKeyListener(new KeyAdapter() {
                @Override
                public void keyPressed(KeyEvent e) {
                    int idx = rowIndex();
                    if ((e.isControlDown() || e.isAltDown()) && e.getKeyCode() == KeyEvent.VK_ENTER) {
                        e.consume();
                        String currentText = multiLineEditField.getText();
                        int caretPos = multiLineEditField.getCaretPosition();
                        String beforeCursor = currentText.substring(0, caretPos);
                        String afterCursor = currentText.substring(caretPos);

                        multiLineEditField.setText(beforeCursor);
                        rawText = beforeCursor;

                        LineRow newRow = insertRowAfter(idx, afterCursor);
                        stopEditing();
                        SwingUtilities.invokeLater(() -> {
                            newRow.startEditing();
                            if (newRow.useMultiLine) {
                                newRow.multiLineEditField.setCaretPosition(0);
                            } else {
                                newRow.singleLineEditField.setCaretPosition(0);
                            }
                        });
                    } else if (e.getKeyCode() == KeyEvent.VK_BACK_SPACE
                            && multiLineEditField.getText().isEmpty() && idx > 0) {
                        e.consume();
                        int prevIdx = idx - 1;
                        String prevText = rows.get(prevIdx).getRawText();
                        stopEditing();
                        removeRow(idx);
                        rows.get(prevIdx).startEditing();
                        LineRow prevRow = rows.get(prevIdx);
                        if (prevRow.useMultiLine) {
                            prevRow.multiLineEditField.setCaretPosition(prevText.length());
                        } else {
                            prevRow.singleLineEditField.setCaretPosition(prevText.length());
                        }
                    } else if (e.getKeyCode() == KeyEvent.VK_ESCAPE) {
                        stopEditing();
                        e.consume();
                    }
                }

                @Override
                public void keyReleased(KeyEvent e) {
                    rawText = multiLineEditField.getText();
                    if (!rawText.trim().startsWith("```")) {
                        useMultiLine = false;
                        switchToSingleLine();
                    } else {
                        updateEditFieldHeight();
                    }
                    notifyContentChanged();
                }
            });

            panel.add(renderedPane, "RENDERED");
            panel.add(singleLineEditField, "EDIT_SINGLE");
            panel.add(multiLineScroll, "EDIT_MULTI");

            showCard("RENDERED");
        }

        private void switchToMultiLine() {
            multiLineEditField.setText(rawText);
            showCard("EDIT_MULTI");
            multiLineEditField.requestFocusInWindow();
            updateEditFieldHeight();
        }

        private void switchToSingleLine() {
            singleLineEditField.setText(rawText);
            showCard("EDIT_SINGLE");
            singleLineEditField.requestFocusInWindow();
            updateEditFieldHeight();
        }

        void startEditing() {
            if (editing) {
                return;
            }
            editing = true;
            if (useMultiLine) {
                multiLineEditField.setText(rawText);
                showCard("EDIT_MULTI");
                multiLineEditField.requestFocusInWindow();
                SwingUtilities.invokeLater(() -> {
                    multiLineEditField.setCaretPosition(multiLineEditField.getText().length());
                    updateEditFieldHeight();
                });
            } else {
                singleLineEditField.setText(rawText);
                showCard("EDIT_SINGLE");
                singleLineEditField.requestFocusInWindow();
                SwingUtilities.invokeLater(() -> {
                    singleLineEditField.setCaretPosition(singleLineEditField.getText().length());
                    updateEditFieldHeight();
                });
            }
        }

        void stopEditing() {
            if (!editing) {
                return;
            }
            editing = false;
            rawText = useMultiLine ? multiLineEditField.getText() : singleLineEditField.getText();
            updateRendered();
            showCard("RENDERED");
            notifyContentChanged();
        }

        private void updateEditFieldHeight() {
            int height;
            if (useMultiLine) {
                java.awt.FontMetrics fm = multiLineEditField.getFontMetrics(multiLineEditField.getFont());
                int lineHeight = fm.getHeight();
                int lineCount = multiLineEditField.getLineCount();
                height = Math.max(28, (lineCount * lineHeight) + 12);
            } else {
                height = 28;
            }

            panel.setPreferredSize(new Dimension(0, height));
            linesPanel.revalidate();
            linesPanel.repaint();
        }

        String getRawText() {
            if (editing) {
                return useMultiLine ? multiLineEditField.getText() : singleLineEditField.getText();
            }
            return rawText;
        }

        private void updateRendered() {
            try {
                String bodyHtml = renderMarkdownLine(rawText);
                String html = buildFullHtml(bodyHtml);

                java.io.StringReader reader = new java.io.StringReader(html);
                renderedPane.read(reader, null);

                SwingUtilities.invokeLater(() -> {
                    int width = linesPanel.getWidth();
                    if (width <= 0) {
                        width = 600;
                    }

                    renderedPane.setSize(width - 40, Integer.MAX_VALUE);
                    Dimension prefSize = renderedPane.getPreferredSize();

                    int lineCount = rawText.split("\n", -1).length;
                    int estimatedHeight = Math.max(28, prefSize.height);

                    if (lineCount > 1) {
                        java.awt.FontMetrics fm = renderedPane.getFontMetrics(renderedPane.getFont());
                        int minHeight = (lineCount * fm.getHeight()) + 20;
                        estimatedHeight = Math.max(estimatedHeight, minHeight);
                    }

                    panel.setPreferredSize(new Dimension(0, estimatedHeight));
                    linesPanel.revalidate();
                    linesPanel.repaint();
                });
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        private void showCard(String card) {
            ((CardLayout) panel.getLayout()).show(panel, card);
        }
    }
}
