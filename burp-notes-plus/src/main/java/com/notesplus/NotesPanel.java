package com.notesplus;

import burp.api.montoya.MontoyaApi;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;

public class NotesPanel extends JPanel {

    private final MarkdownEditorPanel editorPanel;
    private JList<String> notesList;
    private DefaultListModel<String> notesModel;
    private final Path notesDirectory;
    private String currentNote = null;
    private javax.swing.Timer autoSaveTimer;

    public NotesPanel(MontoyaApi api) {
        this.setLayout(new BorderLayout());

        // Initialize notes directory
        this.notesDirectory = initializeNotesDirectory();

        // Initialize notes list components before sidebar creation
        this.notesModel = new DefaultListModel<>();
        this.notesList = new JList<>(notesModel);
        notesList.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
        notesList.setFont(new Font("Segoe UI", Font.PLAIN, 12));
        notesList.addListSelectionListener(e -> {
            if (!e.getValueIsAdjusting()) {
                onNoteSelected();
            }
        });

        // Create sidebar
        JPanel sidebarPanel = createSidebar();

        // Create editor with auto-save callback
        this.editorPanel = new MarkdownEditorPanel(content -> {
            // Auto-save on content change
            if (currentNote != null && !currentNote.isEmpty()) {
                resetAutoSaveTimer();
            }
        });

        // Split pane: sidebar (left) | editor (right)
        JSplitPane splitPane = new JSplitPane(JSplitPane.HORIZONTAL_SPLIT, sidebarPanel, editorPanel.getComponent());
        splitPane.setDividerLocation(200);
        splitPane.setResizeWeight(0.15);

        this.add(splitPane, BorderLayout.CENTER);

        // Load initial notes list
        loadNotesList();
    }

    private JPanel createSidebar() {
        JPanel sidebar = new JPanel(new BorderLayout());
        sidebar.setBackground(new Color(240, 240, 240));
        sidebar.setBorder(new EmptyBorder(10, 10, 10, 10));

        // Header
        JLabel header = new JLabel("Notes");
        header.setFont(new Font("Segoe UI", Font.BOLD, 14));
        sidebar.add(header, BorderLayout.NORTH);

        // Notes list (already initialized in constructor)
        JScrollPane scrollPane = new JScrollPane(notesList);
        scrollPane.setBorder(new EmptyBorder(10, 0, 10, 0));

        JPanel centerPanel = new JPanel(new BorderLayout());
        centerPanel.setOpaque(false);
        centerPanel.add(scrollPane, BorderLayout.CENTER);
        sidebar.add(centerPanel, BorderLayout.CENTER);

        // Footer buttons
        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.CENTER, 5, 0));
        buttonPanel.setOpaque(false);

        JButton newButton = new JButton("+ New");
        newButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        newButton.addActionListener(e -> createNewNote());

        JButton deleteButton = new JButton("Delete");
        deleteButton.setFont(new Font("Segoe UI", Font.PLAIN, 11));
        deleteButton.addActionListener(e -> deleteCurrentNote());

        buttonPanel.add(newButton);
        buttonPanel.add(deleteButton);
        sidebar.add(buttonPanel, BorderLayout.SOUTH);

        return sidebar;
    }

    private Path initializeNotesDirectory() {
        Path notesDir = Paths.get(System.getProperty("user.home"), ".burp_notes_plus");
        try {
            Files.createDirectories(notesDir);
        } catch (IOException e) {
            e.printStackTrace();
        }
        return notesDir;
    }

    private void loadNotesList() {
        notesModel.clear();
        try {
            Files.list(notesDirectory)
                    .filter(Files::isRegularFile)
                    .filter(p -> p.toString().endsWith(".md"))
                    .map(p -> p.getFileName().toString().replaceAll("\\.md$", ""))
                    .sorted()
                    .forEach(notesModel::addElement);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private void onNoteSelected() {
        String selectedNote = notesList.getSelectedValue();
        if (selectedNote != null && !selectedNote.equals(currentNote)) {
            // Save current note if any
            if (currentNote != null) {
                saveCurrentNote();
            }

            // Load selected note
            currentNote = selectedNote;
            Path noteFile = notesDirectory.resolve(selectedNote + ".md");
            try {
                String content = new String(Files.readAllBytes(noteFile), StandardCharsets.UTF_8);
                editorPanel.setContent(content);
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void createNewNote() {
        String noteName = JOptionPane.showInputDialog(this, "Note name:", "Untitled");
        if (noteName != null && !noteName.trim().isEmpty()) {
            noteName = noteName.trim().replaceAll("[^a-zA-Z0-9_\\-]", "_");
            Path noteFile = notesDirectory.resolve(noteName + ".md");

            try {
                if (!Files.exists(noteFile)) {
                    Files.write(noteFile, "".getBytes(StandardCharsets.UTF_8));
                    loadNotesList();

                    // Select the new note
                    int index = notesModel.indexOf(noteName);
                    if (index >= 0) {
                        notesList.setSelectedIndex(index);
                    }
                }
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error creating note: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void deleteCurrentNote() {
        if (currentNote == null) {
            JOptionPane.showMessageDialog(this, "No note selected", "Info", JOptionPane.INFORMATION_MESSAGE);
            return;
        }

        int result = JOptionPane.showConfirmDialog(this, "Delete note: " + currentNote + "?", "Confirm", JOptionPane.YES_NO_OPTION);
        if (result == JOptionPane.YES_OPTION) {
            Path noteFile = notesDirectory.resolve(currentNote + ".md");
            try {
                Files.delete(noteFile);
                currentNote = null;
                editorPanel.setContent("");
                loadNotesList();
            } catch (IOException e) {
                JOptionPane.showMessageDialog(this, "Error deleting note: " + e.getMessage(), "Error", JOptionPane.ERROR_MESSAGE);
            }
        }
    }

    private void saveCurrentNote() {
        if (currentNote != null && !currentNote.isEmpty()) {
            Path noteFile = notesDirectory.resolve(currentNote + ".md");
            try {
                String content = editorPanel.getContent();
                Files.write(noteFile, content.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
    }

    private void resetAutoSaveTimer() {
        if (autoSaveTimer != null) {
            autoSaveTimer.stop();
        }
        autoSaveTimer = new javax.swing.Timer(2000, e -> saveCurrentNote());
        autoSaveTimer.setRepeats(false);
        autoSaveTimer.start();
    }
}
