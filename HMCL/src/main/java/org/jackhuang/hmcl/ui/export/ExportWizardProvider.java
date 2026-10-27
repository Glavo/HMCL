// SPDX-FileCopyrightText: Copyright (C) 2020 The HMCL Authors
// SPDX-License-Identifier: GPL-3.0-or-later WITH AdditionRef-HMCL
package org.jackhuang.hmcl.ui.export;

import javafx.scene.Node;
import org.jackhuang.hmcl.game.HMCLGameInstance;
import org.jackhuang.hmcl.modpack.ModAdviser;
import org.jackhuang.hmcl.modpack.ModpackExportInfo;
import org.jackhuang.hmcl.modpack.mcbbs.McbbsModpackExportTask;
import org.jackhuang.hmcl.modpack.modrinth.ModrinthModpackExportTask;
import org.jackhuang.hmcl.modpack.server.ServerModpackExportTask;
import org.jackhuang.hmcl.task.Task;
import org.jackhuang.hmcl.ui.wizard.WizardController;
import org.jackhuang.hmcl.ui.wizard.WizardProvider;
import org.jackhuang.hmcl.util.SettingsMap;
import org.jetbrains.annotations.Nullable;

import java.nio.file.Path;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public final class ExportWizardProvider implements WizardProvider {
    private final HMCLGameInstance gameInstance;

    public ExportWizardProvider(HMCLGameInstance gameInstance) {
        this.gameInstance = gameInstance;
    }

    @Override
    public void start(SettingsMap settings) {
    }

    /// Creates the export task for the selected format and destination.
    ///
    /// @param settings the wizard settings containing the format, metadata, file selection, and destination
    /// @return the task that exports the selected instance
    /// @throws IllegalStateException if the selected format is unrecognized
    @Override
    public Object finish(SettingsMap settings) {
        List<String> whitelist = settings.get(ModpackFileSelectionPage.MODPACK_FILE_SELECTION);
        Path modpackFile = settings.get(ModpackInfoPage.MODPACK_FILE);
        ModpackExportInfo exportInfo = settings.get(ModpackInfoPage.MODPACK_INFO);
        exportInfo.setWhitelist(whitelist);
        String modpackType = settings.get(ModpackTypeSelectionPage.MODPACK_TYPE);

        return switch (modpackType) {
            case ModpackTypeSelectionPage.MODPACK_TYPE_MCBBS -> exportAsMcbbs(exportInfo, modpackFile);
            case ModpackTypeSelectionPage.MODPACK_TYPE_SERVER -> exportAsServer(exportInfo, modpackFile);
            case ModpackTypeSelectionPage.MODPACK_TYPE_MODRINTH -> exportAsModrinth(exportInfo, modpackFile);
            default -> throw new IllegalStateException("Unrecognized modpack type " + modpackType);
        };
    }

    private Task<?> exportAsMcbbs(ModpackExportInfo exportInfo, Path modpackFile) {
        return new Task<Void>() {
            @Nullable Task<?> dependency;

            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public void execute() {
                dependency = new McbbsModpackExportTask(resolveCurrentGameInstance(), exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        };
    }

    private Task<?> exportAsServer(ModpackExportInfo exportInfo, Path modpackFile) {
        return new Task<Void>() {
            @Nullable Task<?> dependency;

            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public void execute() {
                dependency = new ServerModpackExportTask(resolveCurrentGameInstance(), exportInfo, modpackFile);
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        };
    }

    private Task<?> exportAsModrinth(ModpackExportInfo exportInfo, Path modpackFile) {
        return new Task<Void>() {
            @Nullable Task<?> dependency;

            {
                setSignificance(TaskSignificance.MODERATE);
            }

            @Override
            public void execute() {
                dependency = new ModrinthModpackExportTask(
                        resolveCurrentGameInstance(),
                        exportInfo,
                        modpackFile
                );
            }

            @Override
            public Collection<Task<?>> getDependencies() {
                return Collections.singleton(dependency);
            }
        };
    }

    /// Returns the current registered snapshot for the instance selected by this wizard.
    ///
    /// @return the current registered instance
    private HMCLGameInstance resolveCurrentGameInstance() {
        return gameInstance.getRepository().getInstance(gameInstance.getId());
    }

    @Override
    public Node createPage(WizardController controller, int step, SettingsMap settings) {
        return switch (step) {
            case 0 -> new ModpackTypeSelectionPage(controller);
            case 1 -> new ModpackInfoPage(controller, gameInstance);
            case 2 -> new ModpackFileSelectionPage(controller, gameInstance, ModAdviser::suggestMod);
            default -> throw new IllegalArgumentException("step");
        };
    }

    @Override
    public boolean cancel() {
        return true;
    }
}
