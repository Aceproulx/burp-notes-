package com.notesplus;

import burp.api.montoya.BurpExtension;
import burp.api.montoya.MontoyaApi;

import javax.swing.*;

public class BurpNotesPlusExtension implements BurpExtension {

    @Override
    public void initialize(MontoyaApi api) {
        api.extension().setName("Notes++");

        // Build the UI on the Swing EDT
        SwingUtilities.invokeLater(() -> {
            NotesPanel panel = new NotesPanel(api);
            api.userInterface().registerSuiteTab("Notes++", panel);
        });
    }
}
