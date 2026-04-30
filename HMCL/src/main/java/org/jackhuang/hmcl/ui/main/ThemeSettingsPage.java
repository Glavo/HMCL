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
package org.jackhuang.hmcl.ui.main;

import com.jfoenix.controls.JFXColorPicker;
import com.jfoenix.controls.JFXSlider;
import com.jfoenix.effects.JFXDepthManager;
import javafx.application.Platform;
import javafx.beans.binding.Bindings;
import javafx.beans.binding.StringBinding;
import javafx.beans.binding.When;
import javafx.beans.property.ObjectProperty;
import javafx.beans.property.ReadOnlyObjectProperty;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.value.ChangeListener;
import javafx.beans.value.ObservableValue;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.layout.*;
import org.jackhuang.hmcl.setting.EnumBackgroundImage;
import org.jackhuang.hmcl.theme.ThemeColor;
import org.jackhuang.hmcl.ui.FXUtils;
import org.jackhuang.hmcl.ui.construct.*;
import org.jackhuang.hmcl.ui.decorator.DecoratorPage;

import java.util.Arrays;
import java.util.List;

import static org.jackhuang.hmcl.setting.ConfigHolder.config;
import static org.jackhuang.hmcl.util.i18n.I18n.i18n;

/// @author Glavo
public final class ThemeSettingsPage extends StackPane implements DecoratorPage {

    private static int snapOpacity(double val) {
        if (val <= 0) {
            return 0;
        } else if (Double.isNaN(val) || val >= 100.) {
            return 100;
        }

        int prevTick = (int) (val / 5);
        int prevTickValue = prevTick * 5;
        int nextTickValue = (prevTick + 1) * 5;

        return (val - prevTickValue) > (nextTickValue - val) ? nextTickValue : prevTickValue;
    }

    public ThemeSettingsPage() {
        VBox content = new VBox(10);
        content.setPadding(new Insets(10));
        content.setFillWidth(true);
        ScrollPane scrollPane = new ScrollPane(content);
        FXUtils.smoothScrolling(scrollPane);
        scrollPane.setFitToWidth(true);
        getChildren().setAll(scrollPane);

        var themeSettings = new ComponentList();
        {
            var themeColorPane = new LinePane();
            themeColorPane.setTitle(i18n("settings.launcher.theme"));

            {
                StackPane themeColorPickerContainer = new StackPane();
                themeColorPickerContainer.setMinHeight(30);
                themeColorPane.setRight(themeColorPickerContainer);

                ColorPicker picker = new JFXColorPicker();
                picker.getCustomColors().setAll(ThemeColor.PRESETS.stream().map(ThemeColor.Preset::color).toList());
                // TODO: ThemeColor.bindBidirectional(picker, config().themeColorProperty());
                themeColorPickerContainer.getChildren().setAll(picker);
                Platform.runLater(() -> JFXDepthManager.setDepth(picker, 0));
            }

            themeSettings.getContent().add(themeColorPane);
        }

        {
            var background = new ComponentSublist();
            background.setTitle(i18n("launcher.background"));

            MultiFileItem<EnumBackgroundImage> backgroundItem = new MultiFileItem<>();
            ComponentSublist backgroundSublist = new ComponentSublist();
            backgroundSublist.getContent().add(backgroundItem);
            backgroundSublist.setTitle(i18n("launcher.background"));
            backgroundSublist.setHasSubtitle(true);

            backgroundItem.loadChildren(List.of(
                    new MultiFileItem.Option<>(i18n("launcher.background.default"), EnumBackgroundImage.DEFAULT)
                            .setTooltip(i18n("launcher.background.default.tooltip")),
                    new MultiFileItem.Option<>(i18n("launcher.background.classic"), EnumBackgroundImage.CLASSIC),
                    new MultiFileItem.FileOption<>(i18n("settings.custom"), EnumBackgroundImage.CUSTOM)
                            .setChooserTitle(i18n("launcher.background.choose"))
                            .addExtensionFilter(FXUtils.getImageExtensionFilter())
                            .setSelectionMode(FileSelector.SelectionMode.FILE_OR_DIRECTORY)
                            .bindBidirectional(config().backgroundImageProperty()),
                    new MultiFileItem.StringOption<>(i18n("launcher.background.network"), EnumBackgroundImage.NETWORK)
                            .setValidators(new URLValidator(true))
                            .bindBidirectional(config().backgroundImageUrlProperty()),
                    new MultiFileItem.PaintOption<>(i18n("launcher.background.paint"), EnumBackgroundImage.PAINT)
                            .bindBidirectional(config().backgroundPaintProperty())
            ));
            backgroundItem.selectedDataProperty().bindBidirectional(config().backgroundImageTypeProperty());
            backgroundSublist.subtitleProperty().bind(
                    new When(backgroundItem.selectedDataProperty().isEqualTo(EnumBackgroundImage.DEFAULT))
                            .then(i18n("launcher.background.default"))
                            .otherwise(config().backgroundImageProperty()));

            background.getContent().setAll(backgroundItem);
            themeSettings.getContent().add(background);
        }

        var opacityItem = new LinePane();
        opacityItem.setTitle(i18n("settings.launcher.background.settings.opacity"));
        {
            var right = new HBox(8);
            right.setAlignment(Pos.CENTER_RIGHT);

            JFXSlider slider = new JFXSlider(0, 100,
                    config().getBackgroundImageType() != EnumBackgroundImage.TRANSLUCENT
                            ? config().getBackgroundImageOpacity() : 50);
            FXUtils.setLimitWidth(slider, 300);
            slider.setShowTickMarks(true);
            slider.setMajorTickUnit(10);
            slider.setMinorTickCount(1);
            slider.setBlockIncrement(5);
            slider.setSnapToTicks(true);
            slider.setPadding(new Insets(9, 0, 0, 0));
            HBox.setHgrow(slider, Priority.ALWAYS);

            if (config().getBackgroundImageType() == EnumBackgroundImage.TRANSLUCENT) {
                slider.setDisable(true);
                config().backgroundImageTypeProperty().addListener(new ChangeListener<>() {
                    @Override
                    public void changed(ObservableValue<? extends EnumBackgroundImage> observable, EnumBackgroundImage oldValue, EnumBackgroundImage newValue) {
                        if (newValue != EnumBackgroundImage.TRANSLUCENT) {
                            config().backgroundImageTypeProperty().removeListener(this);
                            slider.setDisable(false);
                            slider.setValue(100);
                        }
                    }
                });
            }

            var placeholder = new Label("100%");
            placeholder.setOpacity(0);

            var textOpacity = new Label();

            StringBinding valueBinding = Bindings.createStringBinding(() -> ((int) slider.getValue()) + "%", slider.valueProperty());
            textOpacity.textProperty().bind(valueBinding);
            slider.setValueFactory(s -> valueBinding);

            slider.valueProperty().addListener((observable, oldValue, newValue) ->
                    config().setBackgroundImageOpacity(snapOpacity(newValue.doubleValue())));

            right.getChildren().setAll(slider, new StackPane(textOpacity, placeholder));
            opacityItem.setRight(right);
            themeSettings.getContent().add(opacityItem);
        }

        content.getChildren().setAll(themeSettings);
        this.getChildren().setAll(content);
    }

    private final ObjectProperty<State> state = new SimpleObjectProperty<>(this, "state", State.fromTitle("TODO")); // TODO

    @Override
    public ReadOnlyObjectProperty<State> stateProperty() {
        return state;
    }
}
