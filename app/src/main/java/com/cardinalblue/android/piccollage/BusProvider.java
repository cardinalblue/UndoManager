package com.cardinalblue.android.piccollage;

import com.squareup.otto.Bus;

/**
 * Created by prada on 9/21/15.
 */
public class BusProvider {
    private static Bus BUS = new Bus();

    public static Bus getInstance() {
        return BUS;
    }
}
