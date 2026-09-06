   
                      
   

package com.haoran.music.common.constant;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

   
                               
   
public final class EmojiPackageCapacity {

    public static final int DEFAULT = 16;
    public static final List<Integer> SUPPORTED = Collections.unmodifiableList(
            Arrays.asList(8, 16, 24, 32, 40));

    private EmojiPackageCapacity() {
    }

    public static boolean isSupported(Integer value) {
        return value != null && SUPPORTED.contains(value);
    }

    public static int effective(Integer value) {
        return isSupported(value) ? value : DEFAULT;
    }
}
