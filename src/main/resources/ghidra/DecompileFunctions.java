//@category Analysis
// Descompila hasta 50 funciones del programa importado y las escribe en un
// archivo de texto. Uso: analyzeHeadless <proyecto> <nombre> -import <so>
// -postScript DecompileFunctions.java <salida>

import ghidra.app.decompiler.DecompInterface;
import ghidra.app.decompiler.DecompileResults;
import ghidra.program.model.listing.Function;
import ghidra.program.model.listing.FunctionIterator;

import java.io.FileWriter;

public class DecompileFunctions extends GhidraScript {

    private static final int MAX_FUNCTIONS = 50;

    @Override
    protected void run() throws Exception {
        String[] args = getScriptArgs();
        String outputPath = args.length > 0 ? args[0] : "decompiled.c";

        DecompInterface decompiler = new DecompInterface();
        try {
            if (!decompiler.openProgram(currentProgram)) {
                printerr("No se pudo abrir el descompilador");
                return;
            }
            try (FileWriter writer = new FileWriter(outputPath)) {
                FunctionIterator functions =
                        currentProgram.getFunctionManager().getFunctions(true);
                int written = 0;
                while (functions.hasNext() && written < MAX_FUNCTIONS) {
                    Function function = functions.next();
                    DecompileResults results =
                            decompiler.decompileFunction(function, 60, monitor);
                    if (results == null || !results.decompileCompleted()
                            || results.getDecompiledFunction() == null) {
                        continue;
                    }
                    writer.write("=== " + function.getName() + " @ "
                            + function.getEntryPoint() + " ===\n");
                    writer.write(results.getDecompiledFunction().getC());
                    writer.write("\n\n");
                    written++;
                }
            }
        } finally {
            decompiler.dispose();
        }
    }
}
