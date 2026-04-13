/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2026 huangyuhui <huanghongxun2008@126.com> and contributors
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <https://www.gnu.org/licenses/>.
 */
package org.jackhuang.hmcl.ui.game;

import com.jfoenix.controls.JFXCheckBox;
import com.jfoenix.controls.JFXTextField;
import com.jfoenix.controls.JFXToggleButton;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.WeakListener;
import javafx.beans.property.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.geometry.Rectangle2D;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Toggle;
import javafx.scene.control.Tooltip;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;
import javafx.stage.Screen;
import org.jackhuang.hmcl.game.HMCLGameRepository;
import org.jackhuang.hmcl.game.ProcessPriority;
import org.jackhuang.hmcl.game.Version;
import org.jackhuang.hmcl.java.JavaManager;
import org.jackhuang.hmcl.java.JavaRuntime;
import org.jackhuang.hmcl.setting.*;
import org.jackhuang.hmcl.setting.property.InheritableProperty;
import org.jackhuang.hmcl.ui.*;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;
import org.jackhuang.hmcl.ui.versions.VersionIconDialog;
import org.jackhuang.hmcl.ui.versions.VersionPage;
import org.jackhuang.hmcl.ui.versions.Versions;
import org.jackhuang.hmcl.util.Pair;
import org.jackhuang.hmcl.util.ServerAddress;
import org.jackhuang.hmcl.util.StringUtils;
import org.jackhuang.hmcl.util.i18n.I18n;
import org.jackhuang.hmcl.util.platform.Architecture;
import org.jackhuang.hmcl.util.platform.OperatingSystem;
import org.jackhuang.hmcl.util.versioning.GameVersionNumber;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.UnknownNullability;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.Pair.pair;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
@NotNullByDefault
public final class GameSettingPage<S extends GameSetting> extends StackPane
        implements DecoratorPage, VersionPage.VersionLoadable, PageAware {

    private final boolean isGlobalSetting;

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", new State("", null, false, false, false));
    private final WeakListenerHolder holder = new WeakListenerHolder();

    /// The selected profile.
    private @Nullable Profile profile;

    /// The current instance ID.
    private @Nullable String instanceId;

    /// The current setting.
    private final ObjectProperty<@Nullable S> currentSetting = new SimpleObjectProperty<>(this, "setting");

    private boolean updatingJavaSetting = false;
    private boolean updatingSelectedJava = false;

    // GUI
    private final VBox rootPane;

    private final @UnknownNullability ImagePickerItem iconPickerItem;

    private final ComponentSublist javaSublist;
    private final MultiFileItem<@Nullable Pair<JavaVersionType, @Nullable JavaRuntime>> javaItem;
    private final MultiFileItem.Option<Pair<JavaVersionType, @Nullable JavaRuntime>> javaAutoDeterminedOption;
    private final MultiFileItem.StringOption<Pair<JavaVersionType, @Nullable JavaRuntime>> javaVersionOption;
    private final MultiFileItem.FileOption<Pair<JavaVersionType, @Nullable JavaRuntime>> javaCustomOption;

    public GameSettingPage(Class<S> settingType) {
        assert settingType == GameSetting.Global.class || settingType == GameSetting.Instance.class;

        this.isGlobalSetting = settingType == GameSetting.Global.class;

        ScrollPane scrollPane = new ScrollPane();
        scrollPane.setFitToHeight(true);
        scrollPane.setFitToWidth(true);
        scrollPane.setVbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        getChildren().setAll(scrollPane);

        this.rootPane = new VBox();
        rootPane.setFillWidth(true);
        scrollPane.setContent(rootPane);
        FXUtils.smoothScrolling(scrollPane);
        rootPane.getStyleClass().add("card-list");
        scrollPane.setContent(rootPane);

        var basicSettings = new ComponentList();
        rootPane.getChildren().add(basicSettings);
        {
            if (isGlobalSetting) {
                iconPickerItem = null;
            } else {
                iconPickerItem = new ImagePickerItem();
                basicSettings.getContent().add(iconPickerItem);
                iconPickerItem.setImage(FXUtils.newBuiltinImage("/assets/img/icon.png"));
                iconPickerItem.setTitle(i18n("settings.icon"));
                iconPickerItem.setOnSelectButtonClicked(e -> onExploreIcon());
                iconPickerItem.setOnDeleteButtonClicked(e -> onDeleteIcon());

                // TODO
//                var globalSettingPane = new LineSelectButton<>();
//                basicSettings.getContent().add(globalSettingPane);
//                globalSettingPane.setTitle("全局游戏设置"); // TODO: i18n
//                globalSettingPane.setOnAction(event -> Versions.modifyGlobalSettings(profile));
            }

            // Java Setting
            javaSublist = new ComponentSublist();
            basicSettings.getContent().add(javaSublist);
            javaSublist.setTitle(i18n("settings.game.java_directory"));
            javaSublist.setHasSubtitle(true);
            {
                javaItem = new MultiFileItem<>();
                javaSublist.getContent().setAll(javaItem);

                javaAutoDeterminedOption = new MultiFileItem.Option<>(i18n("settings.game.java_directory.auto"), pair(JavaVersionType.AUTO, null));
                javaVersionOption = new MultiFileItem.StringOption<>(i18n("settings.game.java_directory.version"), pair(JavaVersionType.VERSION, null));
                javaVersionOption.setValidators(new NumberValidator(true));
                FXUtils.setLimitWidth(javaVersionOption.getCustomField(), 40);

                javaCustomOption = new MultiFileItem.FileOption<Pair<JavaVersionType, JavaRuntime>>(i18n("settings.custom"), pair(JavaVersionType.CUSTOM, null))
                        .setChooserTitle(i18n("settings.game.java_directory.choose"));
                if (OperatingSystem.CURRENT_OS == OperatingSystem.WINDOWS)
                    javaCustomOption.addExtensionFilter(new FileChooser.ExtensionFilter("Java", "java.exe"));

                holder.add(FXUtils.onWeakChangeAndOperate(JavaManager.getAllJavaProperty(), allJava -> {
                    var options = new ArrayList<MultiFileItem.Option<@Nullable Pair<JavaVersionType, @Nullable JavaRuntime>>>();
                    options.add(javaAutoDeterminedOption);
                    options.add(javaVersionOption);
                    if (allJava != null) {
                        boolean isX86 = Architecture.SYSTEM_ARCH.isX86() && allJava.stream().allMatch(java -> java.getArchitecture().isX86());

                        for (JavaRuntime java : allJava) {
                            options.add(new MultiFileItem.Option<>(
                                    i18n("settings.game.java_directory.template",
                                            java.getVersion(),
                                            isX86 ? i18n("settings.game.java_directory.bit", java.getBits().getBit())
                                                    : java.getPlatform().getArchitecture().getDisplayName()),
                                    pair(JavaVersionType.DETECTED, java))
                                    .setSubtitle(java.getBinary().toString()));
                        }
                    }

                    options.add(javaCustomOption);
                    javaItem.loadChildren(options);
                    initializeSelectedJava();
                }));
            }

            // Isolation Setting

            if (isGlobalSetting) {
                var defaultIsolationTypePane = new LineSelectButton<DefaultIsolationType>();
                basicSettings.getContent().add(defaultIsolationTypePane);
                defaultIsolationTypePane.setTitle("默认版本隔离策略"); // TODO: i18n
                defaultIsolationTypePane.setItems(DefaultIsolationType.values());
                defaultIsolationTypePane.setConverter(Enum::name); // TODO: i18n

                bindGlobalSettingBidirectional(defaultIsolationTypePane.valueProperty(), GameSetting.Global::defaultIsolationTypeProperty);
            } else {
                var isolationPane = new LineToggleButton();
                basicSettings.getContent().add(isolationPane);
                isolationPane.setTitle("版本隔离"); // TODO: i18n
                bindInstanceSettingBidirectional(isolationPane.selectedProperty(), GameSetting.Instance::isolationProperty);
            }

            // TODO: Memory Setting

            // Launcher Visibility Setting
            var launcherVisibilityPane = new LineSelectButton<LauncherVisibility>();
            basicSettings.getContent().add(launcherVisibilityPane);
            launcherVisibilityPane.setTitle(i18n("settings.advanced.launcher_visible"));
            launcherVisibilityPane.setItems(LauncherVisibility.values());
            launcherVisibilityPane.setConverter(e -> i18n("settings.advanced.launcher_visibility." + e.name().toLowerCase(Locale.ROOT)));
            bindSettingBidirectional(launcherVisibilityPane.valueProperty(), GameSetting::launcherVisibilityProperty);

            // Game Window Setting
            var windowTypePane = new LineSelectButton<GameWindowType>();
            basicSettings.getContent().add(windowTypePane);
            windowTypePane.setTitle("游戏窗口类型"); // TODO: i18n
            windowTypePane.setItems(GameWindowType.values());
            windowTypePane.setConverter(Enum::name); // TODO: i18n
            bindSettingBidirectional(windowTypePane.valueProperty(), GameSetting::windowTypeProperty);

            // Show Logs Window Setting
            var showLogsPane = createInheritableBooleanButton(GameSetting::showLogsProperty);
            basicSettings.getContent().add(showLogsPane);
            showLogsPane.setTitle(i18n("settings.show_log"));

            // Enable Debug Log Output Setting
            var enableDebugLogOutputPane = createInheritableBooleanButton(GameSetting::enableDebugLogOutputProperty);
            basicSettings.getContent().add(enableDebugLogOutputPane);
            enableDebugLogOutputPane.setTitle(i18n("settings.enable_debug_log_output"));

            // Process Priority Setting
            var processPriorityPane = new LineSelectButton<ProcessPriority>();
            basicSettings.getContent().add(processPriorityPane);
            processPriorityPane.setTitle(i18n("settings.advanced.process_priority"));
            processPriorityPane.setConverter(e -> i18n("settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT)));
            processPriorityPane.setDescriptionConverter(e -> {
                String bundleKey = "settings.advanced.process_priority." + e.name().toLowerCase(Locale.ROOT) + ".desc";
                return I18n.hasKey(bundleKey) ? i18n(bundleKey) : null;
            });
            processPriorityPane.setItems(ProcessPriority.values());
            bindSettingBidirectional(processPriorityPane.valueProperty(), GameSetting::processPriorityProperty);

            // Server Pane
            // TODO: Quick Play?
            var serverPane = new LinePane();
            basicSettings.getContent().add(serverPane); // TODO: Move to other pane?
            serverPane.setTitle(i18n("settings.advanced.server_ip"));
            {
                var txtServerIP = new JFXTextField();
                serverPane.setRight(txtServerIP);
                txtServerIP.setPromptText(i18n("settings.advanced.server_ip.prompt"));
                Validator.addTo(txtServerIP).accept(str -> {
                    if (StringUtils.isBlank(str))
                        return true;
                    try {
                        ServerAddress.parse(str);
                        return true;
                    } catch (Exception ignored) {
                        return false;
                    }
                });
                FXUtils.setLimitWidth(txtServerIP, 300);
                bindSettingBidirectional(txtServerIP.textProperty(), GameSetting::serverIPProperty);
            }

        }

        var customCommand = new ComponentList();
        rootPane.getChildren().addAll(
                createTitle(i18n("settings.advanced.custom_commands"), i18n("settings.advanced.custom_commands.hint"), null),
                customCommand
        );
        {
            var gameArgsPane = new LinePane();
            customCommand.getContent().add(gameArgsPane);
            gameArgsPane.setTitle(i18n("settings.advanced.minecraft_arguments"));
            {
                var gameArgsField = new JFXTextField();
                gameArgsPane.setRight(gameArgsField);
                gameArgsField.setPromptText(i18n("settings.advanced.minecraft_arguments.prompt"));
                gameArgsField.setPrefWidth(300);

                bindSettingBidirectional(gameArgsField.textProperty(), GameSetting::gameArgsProperty);
            }

            var preLaunchCommandPane = new LinePane();
            customCommand.getContent().add(preLaunchCommandPane);
            preLaunchCommandPane.setTitle(i18n("settings.advanced.precall_command"));
            {
                var preLaunchCommandField = new JFXTextField();
                preLaunchCommandPane.setRight(preLaunchCommandField);
                preLaunchCommandField.setPromptText(i18n("settings.advanced.precall_command.prompt"));
                preLaunchCommandField.setPrefWidth(300);

                bindSettingBidirectional(preLaunchCommandField.textProperty(), GameSetting::preLaunchCommandProperty);
            }

            var commandWrapperPane = new LinePane();
            customCommand.getContent().add(commandWrapperPane);
            commandWrapperPane.setTitle(i18n("settings.advanced.wrapper_launcher"));
            {
                var commandWrapperField = new JFXTextField();
                commandWrapperPane.setRight(commandWrapperField);
                commandWrapperField.setPromptText(i18n("settings.advanced.wrapper_launcher.prompt"));
                commandWrapperField.setPrefWidth(300);

                bindSettingBidirectional(commandWrapperField.textProperty(), GameSetting::commandWrapperProperty);
            }

            var postExitCommandPane = new LinePane();
            customCommand.getContent().add(postExitCommandPane);
            postExitCommandPane.setTitle(i18n("settings.advanced.post_exit_command"));
            {
                var postExitCommandField = new JFXTextField();
                postExitCommandPane.setRight(postExitCommandField);
                postExitCommandField.setPromptText(i18n("settings.advanced.post_exit_command.prompt"));
                postExitCommandField.setPrefWidth(300);

                bindSettingBidirectional(postExitCommandField.textProperty(), GameSetting::postExitCommandProperty);
            }
        }


    }

    // region Helper Methods for UI

    private Pane createTitle(String title,
                             @Nullable String tooltip,
                             @Nullable BooleanProperty inheritGlobalSettings) {

        var box = new HBox(8);
        box.setAlignment(Pos.CENTER_LEFT);
        box.setPadding(new Insets(8, 0, 0, 0));

        box.getChildren().add(new Label(title));

        if (tooltip != null) {
            var icon = new StackPane(SVG.INFO.createIcon(16));
            FXUtils.installFastTooltip(icon, tooltip);
            box.getChildren().add(icon);
        }

        if (!isGlobalSetting) { // TODO: use inheritGlobalSettings
            var padding = new StackPane();
            HBox.setHgrow(padding, Priority.ALWAYS);

            var inherit = new JFXCheckBox();
            box.getChildren().addAll(padding, new Label("继承全局设置"), inherit);
        }

        return box;
    }

    private <T> void bindSettingBidirectional(Property<T> property, Function<S, Property<T>> propertyGetter) {
        currentSetting.addListener((observable, oldValue, newValue) -> {
            if (oldValue != null)
                property.unbindBidirectional(propertyGetter.apply(oldValue));

            if (newValue != null)
                property.bindBidirectional(propertyGetter.apply(newValue));
        });
    }

    @SuppressWarnings("unchecked")
    private <T> void bindInstanceSettingBidirectional(Property<T> property, Function<GameSetting.Instance, Property<T>> propertyGetter) {
        assert !isGlobalSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    @SuppressWarnings("unchecked")
    private <T> void bindGlobalSettingBidirectional(Property<T> property, Function<GameSetting.Global, Property<T>> propertyGetter) {
        assert isGlobalSetting;

        bindSettingBidirectional(property, (Function<S, Property<T>>) propertyGetter);
    }

    /// @see #createInheritableBooleanButton(Function)
    private static final class InheritableBooleanBidirectionalBinding implements InvalidationListener, WeakListener {
        private final boolean isGlobalSetting;
        private final WeakReference<LineSelectButton<@Nullable Boolean>> buttonRef;
        private final WeakReference<InheritableProperty<Boolean>> propertyRef;
        private final int hashCode;

        private boolean updating = false;

        private InheritableBooleanBidirectionalBinding(boolean isGlobal,
                                                       LineSelectButton<@Nullable Boolean> button,
                                                       InheritableProperty<Boolean> property) {
            this.isGlobalSetting = isGlobal;
            this.buttonRef = new WeakReference<>(button);
            this.propertyRef = new WeakReference<>(property);
            this.hashCode = System.identityHashCode(button) ^ System.identityHashCode(property);
        }

        @Override
        public void invalidated(Observable sourceProperty) {
            if (!updating) {
                final LineSelectButton<@Nullable Boolean> button = buttonRef.get();
                final InheritableProperty<Boolean> property = propertyRef.get();

                if (button == null || property == null) {
                    if (button != null) {
                        button.valueProperty().removeListener(this);
                    }

                    if (property != null) {
                        property.removeListener(this);
                    }
                } else {
                    updating = true;
                    try {
                        if (property == sourceProperty) {
                            @Nullable Boolean newValue = property.getValue();
                            if (newValue == null) {
                                button.setValue(isGlobalSetting ? property.defaultValue() : null);
                            } else {
                                button.setValue(newValue);
                            }
                        } else {
                            property.setValue(button.getValue());
                        }
                    } finally {
                        updating = false;
                    }
                }
            }
        }

        @Override
        public boolean wasGarbageCollected() {
            return buttonRef.get() == null || propertyRef.get() == null;
        }

        @Override
        public int hashCode() {
            return hashCode;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o)
                return true;
            if (!(o instanceof GameSettingPage.InheritableBooleanBidirectionalBinding that))
                return false;

            final var button = this.buttonRef.get();
            final var property = this.propertyRef.get();

            final var thatColorPicker = that.buttonRef.get();
            final var thatProperty = that.propertyRef.get();

            if (button == null || property == null || thatColorPicker == null || thatProperty == null)
                return false;

            return button == thatColorPicker && property == thatProperty;
        }
    }

    private LineSelectButton<@Nullable Boolean> createInheritableBooleanButton(
            Function<S, InheritableProperty<Boolean>> propertyGetter) {
        var button = new LineSelectButton<@Nullable Boolean>();
        if (isGlobalSetting) {
            button.setItems(true, false);
        } else {
            button.setItems(null, true, false);
        }

        // TODO: i18n
        button.setConverter2(it -> {
            if (it == null) {
                return "继承全局设置";
            } else
                return it ? "启用" : "禁用";
        });

        this.currentSetting.addListener((o, oldValue, newValue) -> {
            if (oldValue != null) {
                var property = propertyGetter.apply(oldValue);
                button.setValue(isGlobalSetting ? property.defaultValue() : null);

                var binding = new InheritableBooleanBidirectionalBinding(isGlobalSetting, button, property);
                button.valueProperty().removeListener(binding);
                oldValue.removeListener(binding);
            }

            if (newValue != null) {
                var property = propertyGetter.apply(newValue);
                button.setValue(isGlobalSetting && property.getValue() == null ? property.defaultValue() : property.getValue());

                var binding = new InheritableBooleanBidirectionalBinding(isGlobalSetting, button, property);
                button.valueProperty().addListener(binding);
                newValue.addListener(binding);
            }
        });

        return button;
    }

    // endregion

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }

    @SuppressWarnings("unchecked")
    @Override
    public void loadVersion(Profile profile, @Nullable String instanceId) {
        this.profile = profile;
        this.instanceId = instanceId;

        assert isGlobalSetting == (instanceId == null);

        if (instanceId != null) {
            this.currentSetting.set((S) new GameSetting.Instance()); // TODO: for test UI
        } else {
            this.currentSetting.set((S) new GameSetting.Global()); // TODO: for test UI
        }
    }

    private void loadIcon() {
        if (profile == null || instanceId == null)
            return;

        iconPickerItem.setImage(profile.getRepository().getVersionIconImage(instanceId));
    }

    private void initializeSelectedJava() {
        S setting = currentSetting.get();

        if (setting == null || updatingJavaSetting)
            return;

        updatingSelectedJava = true;
        switch (setting.javaTypeProperty().getValue()) { // TODO: null?
            case CUSTOM:
                javaCustomOption.setSelected(true);
                break;
            case VERSION:
                javaVersionOption.setSelected(true);
                javaVersionOption.setValue(setting.javaVersionProperty().getValue());
                break;
            case AUTO:
                javaAutoDeterminedOption.setSelected(true);
                break;
            default:
                Toggle toggle = null;
                if (JavaManager.isInitialized()) {
                    try {
                        JavaRuntime java = setting.getJava(null, null);
                        if (java != null) {
                            for (Toggle t : javaItem.getGroup().getToggles()) {
                                if (t.getUserData() != null) {
                                    @SuppressWarnings("unchecked")
                                    var userData = (Pair<JavaVersionType, JavaRuntime>) t.getUserData();
                                    if (userData.getValue() != null && java.getBinary().equals(userData.getValue().getBinary())) {
                                        toggle = t;
                                        break;

                                    }
                                }
                            }
                        }
                    } catch (InterruptedException ignored) {
                    }
                }

                if (toggle != null) {
                    toggle.setSelected(true);
                } else {
                    Toggle selectedToggle = javaItem.getGroup().getSelectedToggle();
                    if (selectedToggle != null) {
                        selectedToggle.setSelected(false);
                    }
                }
                break;
        }
        updatingSelectedJava = false;
    }

    private void initJavaSubtitle() {
        S setting = currentSetting.get();

        if (setting == null || profile == null)
            return;
        initializeSelectedJava();

        HMCLGameRepository repository = this.profile.getRepository();
        JavaVersionType javaVersionType = setting.javaTypeProperty().getValue();
        boolean autoSelected = javaVersionType == JavaVersionType.AUTO || javaVersionType == JavaVersionType.VERSION;

        if (instanceId == null && autoSelected) {
            javaSublist.setSubtitle(i18n("settings.game.java_directory.auto"));
            return;
        }

        var selectedData = javaItem.getSelectedData();
        if (selectedData != null && selectedData.getValue() != null) {
            javaSublist.setSubtitle(selectedData.getValue().getBinary().toString());
            return;
        }

        if (JavaManager.isInitialized()) {
            GameVersionNumber gameVersionNumber;
            Version version;
            if (this.instanceId == null) {
                gameVersionNumber = GameVersionNumber.unknown();
                version = null;
            } else {
                gameVersionNumber = GameVersionNumber.asGameVersion(repository.getGameVersion(this.instanceId));
                version = repository.getResolvedVersion(this.instanceId);
            }

            try {
                JavaRuntime java = setting.getJava(gameVersionNumber, version);
                if (java != null) {
                    javaSublist.setSubtitle(java.getBinary().toString());
                } else {
                    javaSublist.setSubtitle(autoSelected ? i18n("settings.game.java_directory.auto.not_found") : i18n("settings.game.java_directory.invalid"));
                }
                return;
            } catch (InterruptedException ignored) {
            }
        }

        javaSublist.setSubtitle("");
    }

    private void editSpecificSettings() {
        if (profile != null)
            Versions.modifyGameSettings(profile, profile.getSelectedVersion());
    }

    private void onExploreIcon() {
        if (profile == null || instanceId == null)
            return;

        Controllers.dialog(new VersionIconDialog(profile, instanceId, this::loadIcon));
    }

    private void onDeleteIcon() {
        if (profile == null || instanceId == null)
            return;

        profile.getRepository().deleteIconFile(instanceId);
        VersionSetting localVersionSetting = profile.getRepository().getLocalVersionSettingOrCreate(instanceId);
        if (localVersionSetting != null) {
            localVersionSetting.setVersionIcon(VersionIconType.DEFAULT);
        }
        loadIcon();
    }

    private static List<String> getSupportedResolutions() {
        int maxScreenWidth = 0;
        int maxScreenHeight = 0;

        for (Screen screen : Screen.getScreens()) {
            Rectangle2D bounds = screen.getBounds();
            int screenWidth = (int) (bounds.getWidth() * screen.getOutputScaleX());
            int screenHeight = (int) (bounds.getHeight() * screen.getOutputScaleY());

            maxScreenWidth = Math.max(maxScreenWidth, screenWidth);
            maxScreenHeight = Math.max(maxScreenHeight, screenHeight);
        }

        List<String> resolutions = new ArrayList<>(List.of("854x480", "1280x720", "1600x900"));

        if (maxScreenWidth >= 1920 && maxScreenHeight >= 1080) resolutions.add("1920x1080");
        if (maxScreenWidth >= 2560 && maxScreenHeight >= 1440) resolutions.add("2560x1440");
        if (maxScreenWidth >= 3840 && maxScreenHeight >= 2160) resolutions.add("3840x2160");

        return resolutions;
    }

}
