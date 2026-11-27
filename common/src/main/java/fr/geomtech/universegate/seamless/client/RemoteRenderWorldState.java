package fr.geomtech.universegate.seamless.client;

//? if >=1.21.11 {
/*import net.minecraft.util.Util;
*///?} else {
import net.minecraft.Util;
//?}
import fr.geomtech.universegate.mixin.client.SectionRenderDispatcherAccessor;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LevelRenderer;
import net.minecraft.client.renderer.SectionBufferBuilderPack;
import net.minecraft.client.renderer.SectionBufferBuilderPool;
import net.minecraft.client.renderer.ViewArea;
import net.minecraft.client.renderer.chunk.SectionRenderDispatcher;
import net.minecraft.core.BlockPos;
import net.minecraft.core.SectionPos;
import org.jetbrains.annotations.Nullable;

import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Set;

/**
 * Per-remote-world compiled section storage ({@link ViewArea} + {@link SectionRenderDispatcher})
 * allowing remote portal views to compile and render chunk meshes independently without ever
 * calling {@link LevelRenderer#setLevel(ClientLevel)} or triggering {@link LevelRenderer#allChanged()}.
 */
public final class RemoteRenderWorldState {

    private static final Set<SectionRenderDispatcher.RenderSection> ALL_REMOTE_SECTIONS =
            Collections.synchronizedSet(Collections.newSetFromMap(new IdentityHashMap<>()));

    private final ClientLevel level;
    private final int renderDistance;
    private @Nullable SectionRenderDispatcher sectionRenderDispatcher;
    private @Nullable SectionBufferBuilderPool dedicatedBufferPool;
    private @Nullable SectionBufferBuilderPack dedicatedFixedBuffers;
    private @Nullable ViewArea viewArea;
    private double lastCameraX = Double.NaN;
    private double lastCameraY = Double.NaN;
    private double lastCameraZ = Double.NaN;
    private boolean disposed = false;

    public static boolean isRemoteRenderSection(@Nullable SectionRenderDispatcher.RenderSection section) {
        return section != null && ALL_REMOTE_SECTIONS.contains(section);
    }

    public RemoteRenderWorldState(ClientLevel level, int renderDistance) {
        this.level = level;
        this.renderDistance = Math.max(2, Math.min(renderDistance, 8));
    }

    public boolean ensureInitialized(Minecraft minecraft) {
        if (disposed) {
            return false;
        }
        if (viewArea != null && sectionRenderDispatcher != null) {
            return true;
        }
        LevelRenderer levelRenderer = minecraft.levelRenderer;
        if (levelRenderer == null) {
            return false;
        }
        try {
            // Allocate a dedicated SectionBufferBuilderPool and fixed buffer pack per remote world:
            // vanilla's SectionBufferBuilderPool uses an unsynchronized ArrayDeque that assumes a
            // single SectionRenderDispatcher owns it. Sharing minecraft.renderBuffers() causes
            // acquire() races returning null -> Objects.requireNonNull NPE spam on worker threads.
            SectionBufferBuilderPool pool = SectionBufferBuilderPool.allocate(2);
            SectionBufferBuilderPack fixedPack = new SectionBufferBuilderPack();
            SectionRenderDispatcher dispatcher = new SectionRenderDispatcher(
                    level,
                    levelRenderer,
                    Util.backgroundExecutor(),
                    minecraft.renderBuffers(),
                    minecraft.getBlockRenderer(),
                    minecraft.getBlockEntityRenderDispatcher()
            );
            SectionRenderDispatcherAccessor accessor = (SectionRenderDispatcherAccessor) dispatcher;
            accessor.universegate$setBufferPool(pool);
            accessor.universegate$setFixedBuffers(fixedPack);
            this.dedicatedBufferPool = pool;
            this.dedicatedFixedBuffers = fixedPack;
            this.sectionRenderDispatcher = dispatcher;
            this.viewArea = new ViewArea(sectionRenderDispatcher, level, renderDistance, levelRenderer);
            if (this.viewArea.sections != null) {
                for (SectionRenderDispatcher.RenderSection section : this.viewArea.sections) {
                    if (section != null) {
                        ALL_REMOTE_SECTIONS.add(section);
                    }
                }
            }
            return true;
        } catch (Throwable ignored) {
            dispose();
            return false;
        }
    }

    public void repositionCamera(double x, double y, double z) {
        if (disposed || viewArea == null) {
            return;
        }
        if (x == lastCameraX && y == lastCameraY && z == lastCameraZ) return;
        //? if >=1.21.11 {
        /*viewArea.repositionCamera(SectionPos.of(BlockPos.containing(x, y, z)));
        *///?} else {
        viewArea.repositionCamera(x, z);
        //?}
        lastCameraX = x;
        lastCameraY = y;
        lastCameraZ = z;
    }

    public void markSectionDirty(int sectionX, int sectionY, int sectionZ, boolean immediate) {
        if (disposed || viewArea == null) {
            return;
        }
        viewArea.setDirty(sectionX, sectionY, sectionZ, immediate);
    }

    public void markChunkDirty(int chunkX, int chunkZ) {
        if (disposed || viewArea == null) {
            return;
        }
        int count = level.getSectionsCount();
        for (int idx = 0; idx < count; idx++) {
            int sy = level.getSectionYFromSectionIndex(idx);
            viewArea.setDirty(chunkX, sy, chunkZ, false);
        }
    }

    public @Nullable ViewArea getViewArea() {
        return viewArea;
    }

    public @Nullable SectionRenderDispatcher getSectionRenderDispatcher() {
        return sectionRenderDispatcher;
    }

    public ClientLevel getLevel() {
        return level;
    }

    public boolean isDisposed() {
        return disposed;
    }

    /**
     * Deterministically releases all GPU buffers and section worker threads for this remote view.
     */
    public void dispose() {
        if (disposed) {
            return;
        }
        disposed = true;
        ViewArea area = this.viewArea;
        SectionRenderDispatcher dispatcher = this.sectionRenderDispatcher;
        SectionBufferBuilderPool pool = this.dedicatedBufferPool;
        SectionBufferBuilderPack fixedPack = this.dedicatedFixedBuffers;
        this.viewArea = null;
        this.sectionRenderDispatcher = null;
        this.dedicatedBufferPool = null;
        this.dedicatedFixedBuffers = null;
        if (dispatcher != null) {
            try {
                //? if <1.21.11 {
                dispatcher.blockUntilClear();
                //?}
                dispatcher.dispose();
            } catch (Throwable ignored) {
            }
        }
        if (area != null) {
            if (area.sections != null) {
                for (SectionRenderDispatcher.RenderSection section : area.sections) {
                    if (section != null) {
                        ALL_REMOTE_SECTIONS.remove(section);
                    }
                }
            }
            try {
                area.releaseAllBuffers();
            } catch (Throwable ignored) {
            }
        }
        if (fixedPack != null) {
            try {
                fixedPack.close();
            } catch (Throwable ignored) {
            }
        }
        //? if <1.21.11 {
        if (pool != null) {
            while (!pool.isEmpty()) {
                SectionBufferBuilderPack pack = pool.acquire();
                if (pack == null) {
                    break;
                }
                try {
                    pack.close();
                } catch (Throwable ignored) {
                }
            }
        }
        //?}
    }
}
