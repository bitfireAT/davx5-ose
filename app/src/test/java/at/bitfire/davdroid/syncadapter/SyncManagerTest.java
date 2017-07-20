/*
 * Copyright © Ricki Hirner (bitfire web engineering).
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the GNU Public License v3.0
 * which accompanies this distribution, and is available at
 * http://www.gnu.org/licenses/gpl.html
 */

package at.bitfire.davdroid.syncadapter;

import org.junit.Test;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import at.bitfire.davdroid.DavUtils;

import static org.junit.Assert.assertEquals;

public class SyncManagerTest {

    @Test
    public void testUnion() {
        Set<Integer> A = new HashSet<>(Arrays.asList(new Integer[] { 1,2,3 }));
        Set<Integer> B = new HashSet<>(Arrays.asList(new Integer[] { 1,4,5 }));

        assertEquals(new HashSet<>(Arrays.asList(new Integer[] { 2,3,4,5 })), SyncManager.Companion.disjunct(A, B));
        assertEquals(new HashSet<>(Arrays.asList(new Integer[] { 2,3,4,5 })), SyncManager.Companion.disjunct(B, A));
    }

}
