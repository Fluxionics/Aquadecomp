package com.aquadecomp.naming;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class NameInfererTest {

    @TempDir
    Path temp;

    private Path write(String relative, String content) throws Exception {
        Path file = temp.resolve(relative);
        Files.createDirectories(file.getParent());
        Files.writeString(file, content);
        return file;
    }

    @Test
    void detectaNombresOfuscadosYLegitimos() {
        assertTrue(NameInferer.looksObfuscated("a"));
        assertTrue(NameInferer.looksObfuscated("f0"));
        assertTrue(NameInferer.looksObfuscated("ab12"));
        assertTrue(NameInferer.looksObfuscated("f0a"));
        assertFalse(NameInferer.looksObfuscated("contador"));
        assertFalse(NameInferer.looksObfuscated("id"));
        assertFalse(NameInferer.looksObfuscated("texto"));
        assertFalse(NameInferer.looksObfuscated("A"));
        assertFalse(NameInferer.looksObfuscated(""));
        assertFalse(NameInferer.looksObfuscated(null));
    }

    @Test
    void sugiereActividadParaClaseQueHeredaDeActivity() throws Exception {
        write("a.java", """
                public class a extends AppCompatActivity {
                    private String b;
                }
                """);

        NameReport report = NameInferer.infer(temp);

        NameSuggestion clase = report.suggestions().stream()
                .filter(s -> s.kind().equals("clase")).findFirst().orElseThrow();
        assertEquals("a", clase.current());
        assertEquals("Actividad", clase.suggested());
        assertTrue(clase.signals().get(0).contains("AppCompatActivity"),
                "señales: " + clase.signals());
        assertTrue(clase.confidence() <= NameInferer.MAX_CONFIDENCE);
    }

    @Test
    void sugiereNombresParaCamposSegunSuTipo() throws Exception {
        write("b.java", """
                public class b {
                    private String a;
                    private int c = 0;
                    private java.util.List<String> d = new java.util.ArrayList<>();
                }
                """);

        NameReport report = NameInferer.infer(temp);

        assertEquals("texto", find(report, "campo", "a").suggested());
        assertEquals("elementos", find(report, "campo", "d").suggested());
        assertEquals("valor", find(report, "campo", "c").suggested());
        assertTrue(find(report, "campo", "a").confidence() <= NameInferer.MAX_CONFIDENCE);
    }

    @Test
    void contadorSeSugiereCuandoElCampoSeIncrementa() throws Exception {
        write("c.java", """
                public class c {
                    private int a = 0;
                    public void b() {
                        a++;
                    }
                }
                """);

        NameReport report = NameInferer.infer(temp);

        NameSuggestion campo = find(report, "campo", "a");
        assertEquals("contador", campo.suggested());
        assertTrue(campo.signals().get(0).contains("incrementa"),
                "señales: " + campo.signals());
    }

    @Test
    void sugiereOnClickParaOverrideYParaListener() throws Exception {
        write("d.java", """
                public class d implements View.OnClickListener {
                    @Override
                    public void a(View v) {
                    }
                    public void b() {
                        boton.setOnClickListener(this);
                    }
                    public void c(View v) {
                    }
                }
                """);

        NameReport report = NameInferer.infer(temp);

        assertEquals("onClick", find(report, "metodo", "a").suggested());
        assertEquals("onClick", find(report, "metodo", "c").suggested());
        assertTrue(report.suggestions().stream()
                        .noneMatch(s -> s.current().equals("b")),
                "el método b no tiene señal suficiente: " + report.suggestions());
    }

    @Test
    void noSugiereNombresLegitimos() throws Exception {
        write("e.java", """
                public class Principal extends AppCompatActivity {
                    private String texto;
                    public int contador;
                    public void calcular() {
                    }
                }
                """);

        NameReport report = NameInferer.infer(temp);

        assertTrue(report.suggestions().isEmpty(),
                "sugerencias: " + report.suggestions());
        assertEquals(1, report.filesScanned());
    }

    @Test
    void informeIncluyeNotasHonestasYJsonValido() throws Exception {
        write("f.java", """
                public class f {
                    private String a;
                }
                """);

        NameReport report = NameInferer.infer(temp);

        assertFalse(report.notes().isEmpty());
        assertTrue(report.notes().get(0).contains("reglas"),
                "notas: " + report.notes());
        assertTrue(report.notes().stream().anyMatch(n -> n.contains("aprendizaje")),
                "debe decir que no es ML: " + report.notes());
        String json = report.toJson();
        assertTrue(json.contains("\"suggestions\""), "json: " + json);
        assertTrue(json.contains("\"texto\""), "json: " + json);
    }

    @Test
    void directorioInexistenteFallaConMensaje() {
        Path missing = temp.resolve("no-existe");

        IOExceptionFallo fallo = null;
        try {
            NameInferer.infer(missing);
        } catch (java.io.IOException e) {
            fallo = new IOExceptionFallo(e.getMessage());
        }

        assertTrue(fallo != null && fallo.message.contains("director"),
                "mensaje: " + (fallo == null ? "sin fallo" : fallo.message));
    }

    @Test
    void soloLeeArchivosJava() throws Exception {
        write("g.java", "public class g { private String a; }\n");
        write("h.java.bak", "public class h { private String b; }\n");
        write("notas.txt", "public class i { private String c; }\n");

        NameReport report = NameInferer.infer(temp);

        assertEquals(1, report.filesScanned());
        assertTrue(report.suggestions().stream()
                        .noneMatch(s -> s.file().endsWith(".bak")),
                "archivos: " + report.suggestions());
    }

    private NameSuggestion find(NameReport report, String kind, String current) {
        return report.suggestions().stream()
                .filter(s -> s.kind().equals(kind) && s.current().equals(current))
                .findFirst()
                .orElseThrow(() -> new AssertionError("falta sugerencia " + kind + "/"
                        + current + " en " + report.suggestions()));
    }

    private record IOExceptionFallo(String message) {
    }
}
