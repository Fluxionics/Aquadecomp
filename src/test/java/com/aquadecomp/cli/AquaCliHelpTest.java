package com.aquadecomp.cli;

import org.junit.jupiter.api.Test;
import picocli.CommandLine;

import java.io.PrintWriter;
import java.io.StringWriter;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AquaCliHelpTest {

    @Test
    void guiSubcommandAndOptionAreRegistered() {
        CommandLine cmd = new CommandLine(new AquaCli());

        assertTrue(cmd.getSubcommands().containsKey("gui"));
        assertNotNull(cmd.getCommandSpec().optionsMap().get("--gui"));
    }

    @Test
    void rootHelpListsGuiAndOption() {
        StringWriter out = new StringWriter();
        new CommandLine(new AquaCli()).usage(new PrintWriter(out));
        String help = out.toString();

        long mentions = countOccurrences(help, "Inicia la interfaz gráfica");
        assertTrue(mentions >= 2,
                "la ayuda raíz debe listar el subcomando gui y la opción --gui");
        assertTrue(help.contains("--gui"));
        assertTrue(help.contains("equivale al subcomando gui"));
    }

    @Test
    void guiHelpShowsTheEquivalentWaysToLaunch() {
        CommandLine gui = new CommandLine(new AquaCli()).getSubcommands().get("gui");
        StringWriter out = new StringWriter();
        gui.usage(new PrintWriter(out));
        String help = out.toString();

        assertTrue(help.contains("Inicia la interfaz gráfica"));
        assertTrue(help.contains("--gui"));
        assertTrue(help.contains("sin argumentos"));
    }

    @Test
    void helpCommandExitsWithZero() {
        StringWriter out = new StringWriter();
        CommandLine cmd = new CommandLine(new AquaCli());
        cmd.setOut(new PrintWriter(out));
        cmd.setErr(new PrintWriter(out));

        assertEquals(0, cmd.execute("--help"));
        assertTrue(out.toString().contains("gui"));
    }

    @Test
    void standardHelpOptionsAreInSpanish() {
        StringWriter rootOut = new StringWriter();
        new CommandLine(new AquaCli()).usage(new PrintWriter(rootOut));
        assertTrue(rootOut.toString().contains("Muestra esta ayuda y sale."));
        assertTrue(rootOut.toString().contains("Muestra la información de versión y sale."));

        StringWriter guiOut = new StringWriter();
        new CommandLine(new AquaCli()).getSubcommands().get("gui")
                .usage(new PrintWriter(guiOut));
        assertTrue(guiOut.toString().contains("Muestra esta ayuda y sale."));
    }

    @Test
    void rootHelpShowsTheFooterExamples() {
        StringWriter out = new StringWriter();
        new CommandLine(new AquaCli()).usage(new PrintWriter(out));
        String help = out.toString();

        assertTrue(help.contains("java -jar aquedecomp.jar decompile app.apk -o ./salida"));
        assertTrue(help.contains("Sin argumentos se inicia la interfaz gráfica"));
    }

    private static int countOccurrences(String text, String needle) {
        int count = 0;
        int from = 0;
        while (true) {
            int at = text.indexOf(needle, from);
            if (at < 0) {
                return count;
            }
            count++;
            from = at + needle.length();
        }
    }
}
