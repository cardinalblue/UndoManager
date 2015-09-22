package com.cardinalblue.android.piccollage.operation;

import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;

/**
 * Created by prada on 9/21/15.
 */
public class PlusOperation extends BaseCalculateOperation {
    public PlusOperation(int num, int val) {
        super(num, val);
    }

    public PlusOperation(Parcel source) {
        super(source);
    }

    @Override
    public void undo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(number - value));
    }

    public static final Creator<PlusOperation> CREATOR = new Creator<PlusOperation>() {
        public PlusOperation createFromParcel(Parcel source) {
            return new PlusOperation(source);
        }

        public PlusOperation[] newArray(int size) {
            return new PlusOperation[size];
        }
    };
}
