package com.cardinalblue.android.piccollage.operation;


import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;

/**
 * Created by prada on 9/21/15.
 */
public class MultiplyOperation extends BaseCalculateOperation {
    public MultiplyOperation(Parcel source) {
        super(source);
    }
    public MultiplyOperation(int num, int val) {
        super(num, val);
    }

    @Override
    public void undo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(number / value));
    }
    public static final Creator<MultiplyOperation> CREATOR = new Creator<MultiplyOperation>() {
        public MultiplyOperation createFromParcel(Parcel source) {
            return new MultiplyOperation(source);
        }

        public MultiplyOperation[] newArray(int size) {
            return new MultiplyOperation[size];
        }
    };
}
