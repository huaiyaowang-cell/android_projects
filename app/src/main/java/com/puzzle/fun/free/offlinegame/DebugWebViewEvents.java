package com.puzzle.fun.free.offlinegame;

/**
 * EventBus messages from {@link DebugPanelActivity} to {@link MainActivity}.
 */
public final class DebugWebViewEvents {

    private DebugWebViewEvents() {
    }

    public static final class BgLayerShow {
    }

    public static final class BgLayerHide {
    }

    /** Posted after user saves a new background URL in {@link DebugPanelActivity}. */
    public static final class BgUrlChanged {
    }

    public static final class BgInterstitial {
    }

    public static final class TopAlphaPercent {
        public final int percent;

        public TopAlphaPercent(int percent) {
            this.percent = percent;
        }
    }
}
