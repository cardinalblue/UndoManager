package com.cardinalblue.android.piccollage.operation;

import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;
import com.cardinalblue.android.piccollage.UndoManager;

/**
 * Created by prada on 9/21/15.
 */
public abstract class BaseCalculateOperation extends UndoManager.UndoOperation {

    protected final int number;
    protected final int value;

    @Override
    public final void commit() {
        // do nothing
    }

    @Override
    public final void redo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(number));
    }

    public BaseCalculateOperation(int num, int val) {
        this.number = num;
        this.value = val;
    }

    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(this.number);
        dest.writeInt(this.value);
    }

    protected BaseCalculateOperation(Parcel in) {
        this.number = in.readInt();
        this.value = in.readInt();
    }
}
