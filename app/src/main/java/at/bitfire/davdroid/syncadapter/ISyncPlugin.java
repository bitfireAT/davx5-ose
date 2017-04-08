package at.bitfire.davdroid.syncadapter;

import android.content.Context;
import android.content.SyncResult;
import android.support.annotation.NonNull;

interface ISyncPlugin {

    boolean beforeSync(@NonNull Context context, @NonNull SyncResult syncResult);
    void afterSync(@NonNull Context context, @NonNull SyncResult syncResult);

}
