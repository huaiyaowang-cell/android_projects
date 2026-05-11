package com.puzzle.fun.free.offlinegame;

/**
 * EventBus messages from {@link DebugPanelActivity} to {@link MainActivity}.
 */
public final class DebugWebViewEvents {

    private DebugWebViewEvents() {
    }

    public static final class BgInterstitial {
    }

    public static final class TopAlphaPercent {
        public final int percent;

        public TopAlphaPercent(int percent) {
            this.percent = percent;
        }
    }

    public static final class BgLayerAlphaPercent {
        public final int layerIndex;
        public final int percent;

        public BgLayerAlphaPercent(int layerIndex, int percent) {
            this.layerIndex = layerIndex;
            this.percent = percent;
        }
    }
}
