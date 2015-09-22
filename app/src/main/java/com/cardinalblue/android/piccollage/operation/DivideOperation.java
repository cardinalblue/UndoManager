package com.cardinalblue.android.piccollage.operation;

import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;

/**
 * Created by prada on 9/21/15.
 */
public class DivideOperation extends BaseCalculateOperation {
    public DivideOperation(Parcel source) {
        super(source);
    }
    public DivideOperation(int num, int val) {
        super(num, val);
    }

    @Override
    public void undo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(number * value));
    }
    public static final Creator<DivideOperation> CREATOR = new Creator<DivideOperation>() {
        public DivideOperation createFromParcel(Parcel source) {
            return new DivideOperation(source);
        }

        public DivideOperation[] newArray(int size) {
            return new DivideOperation[size];
        }
    };
}
