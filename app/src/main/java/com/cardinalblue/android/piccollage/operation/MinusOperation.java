package com.cardinalblue.android.piccollage.operation;

import android.os.Parcel;

import com.cardinalblue.android.piccollage.BusProvider;
import com.cardinalblue.android.piccollage.NumberUpdateEvent;

/**
 * Created by prada on 9/21/15.
 */
public class MinusOperation extends BaseCalculateOperation {

    public MinusOperation(Parcel source) {
        super(source);
    }
    public MinusOperation(int num, int val) {
        super(num, val);
    }

    @Override
    public void undo() {
        BusProvider.getInstance().post(new NumberUpdateEvent(number + value));
    }
    public static final Creator<MinusOperation> CREATOR = new Creator<MinusOperation>() {
        public MinusOperation createFromParcel(Parcel source) {
            return new MinusOperation(source);
        }

        public MinusOperation[] newArray(int size) {
            return new MinusOperation[size];
        }
    };
}
