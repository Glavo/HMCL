/*
 * Hello Minecraft! Launcher
 * Copyright (C) 2021  huangyuhui <huanghongxun2008@126.com> and contributors
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
package org.jackhuang.hmcl.setting;

import com.google.gson.*;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import javafx.beans.InvalidationListener;
import javafx.beans.Observable;
import javafx.beans.property.*;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.ObservableMap;
import javafx.collections.ObservableSet;
import javafx.scene.paint.Paint;
import org.hildan.fxgson.creators.ObservableListCreator;
import org.hildan.fxgson.creators.ObservableMapCreator;
import org.hildan.fxgson.creators.ObservableSetCreator;
import org.hildan.fxgson.factories.JavaFxPropertyTypeAdapterFactory;
import org.jackhuang.hmcl.Metadata;
import org.jackhuang.hmcl.auth.authlibinjector.AuthlibInjectorServer;
import org.jackhuang.hmcl.util.gson.EnumOrdinalDeserializer;
import org.jackhuang.hmcl.util.gson.FileTypeAdapter;
import org.jackhuang.hmcl.util.gson.PaintAdapter;
import org.jackhuang.hmcl.util.i18n.Locales;
import org.jackhuang.hmcl.util.i18n.Locales.SupportedLocale;
import org.jackhuang.hmcl.util.javafx.DirtyTracker;
import org.jackhuang.hmcl.util.javafx.ObservableHelper;
import org.jackhuang.hmcl.util.javafx.PropertyUtils;
import org.jetbrains.annotations.Nullable;

import java.io.File;
import java.io.IOException;
import java.net.Proxy;
import java.util.Map;
import java.util.TreeMap;
import java.util.function.Function;

import static org.jackhuang.hmcl.util.javafx.PropertyUtils.getPropertyHandleFactories;

public final class Config implements Observable {

    public static final int CURRENT_UI_VERSION = 0;

    public static final Gson CONFIG_GSON = new GsonBuilder()
            .registerTypeAdapter(File.class, FileTypeAdapter.INSTANCE)
            .registerTypeAdapter(ObservableList.class, new ObservableListCreator())
            .registerTypeAdapter(ObservableSet.class, new ObservableSetCreator())
            .registerTypeAdapter(ObservableMap.class, new ObservableMapCreator())
            .registerTypeAdapterFactory(new JavaFxPropertyTypeAdapterFactory(true, true))
            .registerTypeAdapter(EnumBackgroundImage.class, new EnumOrdinalDeserializer<>(EnumBackgroundImage.class)) // backward compatibility for backgroundType
            .registerTypeAdapter(Proxy.Type.class, new EnumOrdinalDeserializer<>(Proxy.Type.class)) // backward compatibility for hasProxy
            .registerTypeAdapter(Paint.class, new PaintAdapter())
            .setPrettyPrinting()
            .setObjectToNumberStrategy(ToNumberPolicy.LONG_OR_DOUBLE)
            .create();

    private static final Map<String, Function<Object, PropertyUtils.PropertyHandle>> FACTORIES =
            getPropertyHandleFactories(Config.class);

    @Nullable
    public static Config fromJson(String json) throws JsonParseException {
        Config loaded = CONFIG_GSON.fromJson(json, Config.class);
        if (loaded == null) {
            return null;
        }
        Config instance = new Config();
        PropertyUtils.copyProperties(loaded, instance);
        return instance;
    }

    private transient final ObservableHelper helper = new ObservableHelper(this);
    private transient final DirtyTracker tracker = new DirtyTracker();

    public Config() {
        for (var function : FACTORIES.values()) {
            Observable observable = function.apply(this).observable;
            observable.addListener(helper);
            observable.addListener(tracker);
        }
    }

    @Override
    public void addListener(InvalidationListener listener) {
        helper.addListener(listener);
    }

    @Override
    public void removeListener(InvalidationListener listener) {
        helper.removeListener(listener);
    }

    public String toJson() {
        return CONFIG_GSON.toJson(this);
    }

    private final StringProperty selectedProfile = new SimpleStringProperty(this, "last", "");

    public StringProperty selectedProfileProperty() {
        return selectedProfile;
    }

    public String getSelectedProfile() {
        return selectedProfile.get();
    }

    public void setSelectedProfile(String selectedProfile) {
        this.selectedProfile.set(selectedProfile);
    }

    private final ObjectProperty<EnumBackgroundImage> backgroundImageType =
            new SimpleObjectProperty<>(this, "backgroundType", EnumBackgroundImage.DEFAULT);

    public ObjectProperty<EnumBackgroundImage> backgroundImageTypeProperty() {
        return backgroundImageType;
    }

    public EnumBackgroundImage getBackgroundImageType() {
        return backgroundImageType.get();
    }

    public void setBackgroundImageType(EnumBackgroundImage backgroundImageType) {
        this.backgroundImageType.set(backgroundImageType);
    }

    private final StringProperty backgroundImage = new SimpleStringProperty(this, "bgpath");

    public StringProperty backgroundImageProperty() {
        return backgroundImage;
    }

    public String getBackgroundImage() {
        return backgroundImage.get();
    }

    public void setBackgroundImage(String backgroundImage) {
        this.backgroundImage.set(backgroundImage);
    }

    private final StringProperty backgroundImageUrl = new SimpleStringProperty(this, "bgurl");

    public StringProperty backgroundImageUrlProperty() {
        return backgroundImageUrl;
    }

    public String getBackgroundImageUrl() {
        return backgroundImageUrl.get();
    }

    public void setBackgroundImageUrl(String backgroundImageUrl) {
        this.backgroundImageUrl.set(backgroundImageUrl);
    }

    private final ObjectProperty<Paint> backgroundPaint = new SimpleObjectProperty<>(this, "bgpaint");

    public Paint getBackgroundPaint() {
        return backgroundPaint.get();
    }

    public ObjectProperty<Paint> backgroundPaintProperty() {
        return backgroundPaint;
    }

    public void setBackgroundPaint(Paint backgroundPaint) {
        this.backgroundPaint.set(backgroundPaint);
    }

    private final IntegerProperty backgroundImageOpacity = new SimpleIntegerProperty(this, "bgImageOpacity", 100);

    public IntegerProperty backgroundImageOpacityProperty() {
        return backgroundImageOpacity;
    }

    public int getBackgroundImageOpacity() {
        return backgroundImageOpacity.get();
    }

    public void setBackgroundImageOpacity(int backgroundImageOpacity) {
        this.backgroundImageOpacity.set(backgroundImageOpacity);
    }

    private final ObjectProperty<EnumCommonDirectory> commonDirType =
            new SimpleObjectProperty<>(this, "commonDirType", EnumCommonDirectory.DEFAULT);

    public ObjectProperty<EnumCommonDirectory> commonDirTypeProperty() {
        return commonDirType;
    }

    public EnumCommonDirectory getCommonDirType() {
        return commonDirType.get();
    }

    public void setCommonDirType(EnumCommonDirectory commonDirType) {
        this.commonDirType.set(commonDirType);
    }

    private final StringProperty commonDirectory =
            new SimpleStringProperty(this, "commonpath", Metadata.MINECRAFT_DIRECTORY.toString());

    public StringProperty commonDirectoryProperty() {
        return commonDirectory;
    }

    public String getCommonDirectory() {
        return commonDirectory.get();
    }

    public void setCommonDirectory(String commonDirectory) {
        this.commonDirectory.set(commonDirectory);
    }

    private final BooleanProperty hasProxy = new SimpleBooleanProperty(this, "hasProxy");

    public BooleanProperty hasProxyProperty() {
        return hasProxy;
    }

    public boolean hasProxy() {
        return hasProxy.get();
    }

    public void setHasProxy(boolean hasProxy) {
        this.hasProxy.set(hasProxy);
    }

    private final BooleanProperty hasProxyAuth = new SimpleBooleanProperty(this, "hasProxyAuth");

    public BooleanProperty hasProxyAuthProperty() {
        return hasProxyAuth;
    }

    public boolean hasProxyAuth() {
        return hasProxyAuth.get();
    }

    public void setHasProxyAuth(boolean hasProxyAuth) {
        this.hasProxyAuth.set(hasProxyAuth);
    }

    private final ObjectProperty<Proxy.Type> proxyType = new SimpleObjectProperty<>(this, "proxyType", Proxy.Type.HTTP);

    public ObjectProperty<Proxy.Type> proxyTypeProperty() {
        return proxyType;
    }

    public Proxy.Type getProxyType() {
        return proxyType.get();
    }

    public void setProxyType(Proxy.Type proxyType) {
        this.proxyType.set(proxyType);
    }

    private final StringProperty proxyHost = new SimpleStringProperty(this, "proxyHost");

    public StringProperty proxyHostProperty() {
        return proxyHost;
    }

    public String getProxyHost() {
        return proxyHost.get();
    }

    public void setProxyHost(String proxyHost) {
        this.proxyHost.set(proxyHost);
    }

    private final IntegerProperty proxyPort = new SimpleIntegerProperty(this, "proxyPort");

    public IntegerProperty proxyPortProperty() {
        return proxyPort;
    }

    public int getProxyPort() {
        return proxyPort.get();
    }

    public void setProxyPort(int proxyPort) {
        this.proxyPort.set(proxyPort);
    }

    private final StringProperty proxyUser = new SimpleStringProperty(this, "proxyUserName");

    public StringProperty proxyUserProperty() {
        return proxyUser;
    }

    public String getProxyUser() {
        return proxyUser.get();
    }

    public void setProxyUser(String proxyUser) {
        this.proxyUser.set(proxyUser);
    }

    private final StringProperty proxyPass = new SimpleStringProperty(this, "proxyPassword");

    public StringProperty proxyPassProperty() {
        return proxyPass;
    }

    public String getProxyPass() {
        return proxyPass.get();
    }

    public void setProxyPass(String proxyPass) {
        this.proxyPass.set(proxyPass);
    }

    private final DoubleProperty x = new SimpleDoubleProperty(this, "x");

    public DoubleProperty xProperty() {
        return x;
    }

    public double getX() {
        return x.get();
    }

    public void setX(double x) {
        this.x.set(x);
    }

    private final DoubleProperty y = new SimpleDoubleProperty(this, "y");

    public DoubleProperty yProperty() {
        return y;
    }

    public double getY() {
        return y.get();
    }

    public void setY(double y) {
        this.y.set(y);
    }

    private final DoubleProperty width = new SimpleDoubleProperty(this, "width");

    public DoubleProperty widthProperty() {
        return width;
    }

    public double getWidth() {
        return width.get();
    }

    public void setWidth(double width) {
        this.width.set(width);
    }

    private final DoubleProperty height = new SimpleDoubleProperty(this, "height");

    public DoubleProperty heightProperty() {
        return height;
    }

    public double getHeight() {
        return height.get();
    }

    public void setHeight(double height) {
        this.height.set(height);
    }

    private final ObjectProperty<Theme> theme = new SimpleObjectProperty<>(this, "theme");

    public ObjectProperty<Theme> themeProperty() {
        return theme;
    }

    public Theme getTheme() {
        return theme.get();
    }

    public void setTheme(Theme theme) {
        this.theme.set(theme);
    }

    private final ObjectProperty<SupportedLocale> localization = new SimpleObjectProperty<>(this, "localization", Locales.DEFAULT);

    public ObjectProperty<SupportedLocale> localizationProperty() {
        return localization;
    }

    public SupportedLocale getLocalization() {
        return localization.get();
    }

    public void setLocalization(SupportedLocale localization) {
        this.localization.set(localization);
    }

    private final BooleanProperty autoDownloadThreads = new SimpleBooleanProperty(this, "autoDownloadThreads", true);

    public BooleanProperty autoDownloadThreadsProperty() {
        return autoDownloadThreads;
    }

    public boolean getAutoDownloadThreads() {
        return autoDownloadThreads.get();
    }

    public void setAutoDownloadThreads(boolean autoDownloadThreads) {
        this.autoDownloadThreads.set(autoDownloadThreads);
    }

    private final IntegerProperty downloadThreads = new SimpleIntegerProperty(this, "downloadThreads", 64);

    public IntegerProperty downloadThreadsProperty() {
        return downloadThreads;
    }

    public int getDownloadThreads() {
        return downloadThreads.get();
    }

    public void setDownloadThreads(int downloadThreads) {
        this.downloadThreads.set(downloadThreads);
    }

    private final StringProperty downloadType = new SimpleStringProperty(this, "downloadType", DownloadProviders.DEFAULT_RAW_PROVIDER_ID);

    public StringProperty downloadTypeProperty() {
        return downloadType;
    }

    public String getDownloadType() {
        return downloadType.get();
    }

    public void setDownloadType(String downloadType) {
        this.downloadType.set(downloadType);
    }

    private final BooleanProperty autoChooseDownloadType = new SimpleBooleanProperty(this, "autoChooseDownloadType", true);

    public BooleanProperty autoChooseDownloadTypeProperty() {
        return autoChooseDownloadType;
    }

    public boolean isAutoChooseDownloadType() {
        return autoChooseDownloadType.get();
    }

    public void setAutoChooseDownloadType(boolean autoChooseDownloadType) {
        this.autoChooseDownloadType.set(autoChooseDownloadType);
    }

    private final StringProperty versionListSource = new SimpleStringProperty(this, "versionListSource", "balanced");

    public StringProperty versionListSourceProperty() {
        return versionListSource;
    }

    public String getVersionListSource() {
        return versionListSource.get();
    }

    public void setVersionListSource(String versionListSource) {
        this.versionListSource.set(versionListSource);
    }

    private final SimpleMapProperty<String, Profile> configurations = new SimpleMapProperty<>(this, "configurations", FXCollections.observableMap(new TreeMap<>()));

    public MapProperty<String, Profile> getConfigurations() {
        return configurations;
    }

    private final StringProperty selectedAccount = new SimpleStringProperty(this, "selectedAccount");

    public StringProperty selectedAccountProperty() {
        return selectedAccount;
    }

    public String getSelectedAccount() {
        return selectedAccount.get();
    }

    public void setSelectedAccount(String selectedAccount) {
        this.selectedAccount.set(selectedAccount);
    }

    private final ListProperty<Map<Object, Object>> accountStorages = new SimpleListProperty<>(this, "accounts", FXCollections.observableArrayList());

    public ObservableList<Map<Object, Object>> getAccountStorages() {
        return accountStorages;
    }

    private final StringProperty fontFamily = new SimpleStringProperty(this, "fontFamily");

    public StringProperty fontFamilyProperty() {
        return fontFamily;
    }

    public String getFontFamily() {
        return fontFamily.get();
    }

    public void setFontFamily(String fontFamily) {
        this.fontFamily.set(fontFamily);
    }

    private final DoubleProperty fontSize = new SimpleDoubleProperty(this, "fontSize", 12);

    public DoubleProperty fontSizeProperty() {
        return fontSize;
    }

    public double getFontSize() {
        return fontSize.get();
    }

    public void setFontSize(double fontSize) {
        this.fontSize.set(fontSize);
    }

    private final StringProperty launcherFontFamily = new SimpleStringProperty(this, "launcherFontFamily");

    public StringProperty launcherFontFamilyProperty() {
        return launcherFontFamily;
    }

    public String getLauncherFontFamily() {
        return launcherFontFamily.get();
    }

    public void setLauncherFontFamily(String launcherFontFamily) {
        this.launcherFontFamily.set(launcherFontFamily);
    }

    private final ObjectProperty<Integer> logLines = new SimpleObjectProperty<>(this, "logLines");

    public ObjectProperty<Integer> logLinesProperty() {
        return logLines;
    }

    public Integer getLogLines() {
        return logLines.get();
    }

    public void setLogLines(Integer logLines) {
        this.logLines.set(logLines);
    }

    private final ObservableList<AuthlibInjectorServer> authlibInjectorServers =
            new SimpleListProperty<>(this, "authlibInjectorServers", FXCollections.observableArrayList(server -> new Observable[]{server}));

    public ObservableList<AuthlibInjectorServer> getAuthlibInjectorServers() {
        return authlibInjectorServers;
    }

    private final BooleanProperty addedLittleSkin = new SimpleBooleanProperty(this, "addedLittleSkin", false);

    public BooleanProperty addedLittleSkinProperty() {
        return addedLittleSkin;
    }

    public boolean isAddedLittleSkin() {
        return addedLittleSkin.get();
    }

    public void setAddedLittleSkin(boolean addedLittleSkin) {
        this.addedLittleSkin.set(addedLittleSkin);
    }

    private final BooleanProperty disableAutoGameOptions = new SimpleBooleanProperty(this, "disableAutoGameOptions", false);

    public BooleanProperty disableAutoGameOptionsProperty() {
        return disableAutoGameOptions;
    }

    public boolean isDisableAutoGameOptions() {
        return disableAutoGameOptions.get();
    }

    public void setDisableAutoGameOptions(boolean disableAutoGameOptions) {
        this.disableAutoGameOptions.set(disableAutoGameOptions);
    }

    private final IntegerProperty configVersion = new SimpleIntegerProperty(this, "_version", 0);

    public IntegerProperty configVersionProperty() {
        return configVersion;
    }

    public int getConfigVersion() {
        return configVersion.get();
    }

    public void setConfigVersion(int configVersion) {
        this.configVersion.set(configVersion);
    }

    /**
     * The version of UI that the user have last used.
     * If there is a major change in UI, {@link Config#CURRENT_UI_VERSION} should be increased.
     * When {@link #CURRENT_UI_VERSION} is higher than the property, the user guide should be shown,
     * then this property is set to the same value as {@link #CURRENT_UI_VERSION}.
     * In particular, the property is default to 0, so that whoever open the application for the first time will see the guide.
     */
    private final IntegerProperty uiVersion = new SimpleIntegerProperty(this, "uiVersion", 0);

    public IntegerProperty uiVersionProperty() {
        return uiVersion;
    }

    public int getUiVersion() {
        return uiVersion.get();
    }

    public void setUiVersion(int uiVersion) {
        this.uiVersion.set(uiVersion);
    }

    /**
     * The preferred login type to use when the user wants to add an account.
     */
    private final StringProperty preferredLoginType = new SimpleStringProperty(this, "preferredLoginType");

    public StringProperty preferredLoginTypeProperty() {
        return preferredLoginType;
    }

    public String getPreferredLoginType() {
        return preferredLoginType.get();
    }

    public void setPreferredLoginType(String preferredLoginType) {
        this.preferredLoginType.set(preferredLoginType);
    }

    private final BooleanProperty animationDisabled = new SimpleBooleanProperty(this, "animationDisabled");

    public BooleanProperty animationDisabledProperty() {
        return animationDisabled;
    }

    public boolean isAnimationDisabled() {
        return animationDisabled.get();
    }

    public void setAnimationDisabled(boolean animationDisabled) {
        this.animationDisabled.set(animationDisabled);
    }

    private final BooleanProperty titleTransparent = new SimpleBooleanProperty(this, "titleTransparent", false);

    public BooleanProperty titleTransparentProperty() {
        return titleTransparent;
    }

    public boolean isTitleTransparent() {
        return titleTransparent.get();
    }

    public void setTitleTransparent(boolean titleTransparent) {
        this.titleTransparent.set(titleTransparent);
    }

    private final StringProperty promptedVersion = new SimpleStringProperty(this, "promptedVersion", null);

    public StringProperty promptedVersionProperty() {
        return promptedVersion;
    }

    public String getPromptedVersion() {
        return promptedVersion.get();
    }

    public void setPromptedVersion(String promptedVersion) {
        this.promptedVersion.set(promptedVersion);
    }

    private final MapProperty<String, Object> shownTips = new SimpleMapProperty<>(this, "shownTips", FXCollections.observableHashMap());

    public ObservableMap<String, Object> getShownTips() {
        return shownTips;
    }

    public static final class Adapter extends TypeAdapter<Config> {
        @Override
        public Config read(JsonReader in) throws IOException {


            return null; // TODO
        }

        @Override
        public void write(JsonWriter out, Config value) throws IOException {
            // TODO
        }
    }
}
