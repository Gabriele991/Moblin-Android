package com.dimadesu.lifestreamer.uvc;

import androidx.annotation.Nullable;

import com.serenegiant.usb.Format;
import com.serenegiant.usb.Size;
import com.serenegiant.usb.UVCCamera;

import java.util.List;

/**
 * Picks the best *advertised* USB camera mode; never fabricates 4K support.
 * The chosen mode is a USB input mode. Output encoding has separate settings.
 */
public final class UvcPresetSelector {
    private UvcPresetSelector() {}

    @Nullable
    public static Size bestMjpeg30(@Nullable List<Format> formats, @Nullable Size current) {
        return bestForOutput(formats, current, 3840, 2160);
    }

    @Nullable
    public static Size bestForOutput(@Nullable List<Format> formats, @Nullable Size current,
                                     int targetWidth, int targetHeight) {
        if (formats == null || current == null) return null;

        Size best = null;
        long bestScore = Long.MIN_VALUE;

        for (Format format : formats) {
            final boolean mjpeg = format.type == UVCCamera.UVC_VS_FORMAT_MJPEG;
            final boolean yuv = format.type == UVCCamera.UVC_VS_FORMAT_UNCOMPRESSED;
            if (!mjpeg && !yuv) continue;

            for (Format.Descriptor descriptor : format.frameDescriptors) {
                final int w = descriptor.width;
                final int h = descriptor.height;
                // Match the maximum resolution actually observed from Pocket 4 Pro.
                if (w < 640 || h < 360 || w > 3840 || h > 2160) continue;

                for (Format.Interval interval : descriptor.intervals) {
                    final int fps = interval.fps;
                    if (fps <= 0 || fps > 30) continue;

                    // Match the stream output where possible: decoding unnecessary
                    // 4K USB frames for a 1080p live overloads older phones.
                    final long area = (long) w * h;
                    final boolean fitsOutput = w <= targetWidth && h <= targetHeight;
                    // On older phones 4K@15 may overwhelm the USB/decoder path.
                    // Prefer a real 30 fps advertised mode over a larger slow mode.
                    // Only then optimize resolution, format and aspect ratio.
                    final long score = (fps >= 30 ? 100_000_000_000_000L : (long) fps * 1_000_000_000_000L)
                            + (fitsOutput ? 100_000_000_000L : 0L)
                            + (mjpeg ? 10_000_000_000L : 0L)
                            + (Math.abs((long) w * 9 - (long) h * 16) < 16 ? 1_000_000_000L : 0L)
                            + (fitsOutput ? area : -area);

                    if (score > bestScore) {
                        Size candidate = current.clone();
                        candidate.type = mjpeg ? UVCCamera.UVC_VS_FRAME_MJPEG : UVCCamera.UVC_VS_FRAME_UNCOMPRESSED;
                        candidate.width = w;
                        candidate.height = h;
                        candidate.fps = fps;
                        best = candidate;
                        bestScore = score;
                    }
                }
            }
        }
        return best;
    }
}
