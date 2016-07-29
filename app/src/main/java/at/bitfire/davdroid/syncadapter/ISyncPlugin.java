package at.bitfire.davdroid.syncadapter;

import android.content.Context;
import android.support.annotation.NonNull;

interface ISyncPlugin {

    boolean beforeSync(@NonNull Context context);
    void afterSync(@NonNull Context context);

}
