package com.aquadecomp;

import com.aquadecomp.cli.AquaCli;

public final class Aquadecomp {

    public static void main(String[] args) {

        if (args.length == 0) {
            com.aquadecomp.gui.AquaGui.launch(args);
            return;
        }

        int exitCode = AquaCli.execute(args);
        System.exit(exitCode);
    }

    private Aquadecomp() {
    }
}
