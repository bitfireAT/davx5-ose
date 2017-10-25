package at.bitfire.davdroid.settings;

import at.bitfire.davdroid.settings.ISettingsObserver;

interface ISettings {

    void forceReload();

    boolean has(String key);

    boolean getBoolean(String key, boolean defaultValue);
    int getInt(String key, int defaultValue);
    long getLong(String key, long defaultValue);
    String getString(String key, String defaultValue);

    boolean isWritable(String key);

    boolean putBoolean(String key, boolean value);
    boolean putInt(String key, int value);
    boolean putLong(String key, long value);
    boolean putString(String key, String value);

    boolean remove(String key);

    void registerObserver(ISettingsObserver observer);
    void unregisterObserver(ISettingsObserver observer);

}
