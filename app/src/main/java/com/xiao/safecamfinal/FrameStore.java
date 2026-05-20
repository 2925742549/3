package com.xiao.safecamfinal;

import java.util.concurrent.atomic.AtomicReference;

final class FrameStore {
    static final AtomicReference<byte[]> latestJpeg = new AtomicReference<>();
    private FrameStore() {}
}
