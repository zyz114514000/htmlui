package net.dimaskama.mcef.api;

import net.dimaskama.mcef.impl.MCEFApiImpl;

import java.util.concurrent.CompletableFuture;

/**
 * Main entry point for interacting with the MCEF Modern API.
 * <p>
 * The {@code MCEFApi} interface provides access to the core functionality of the
 * MCEF (Modern Chromium Embedded Framework) system, including initialization
 * and browser instance creation.
 * <p>
 * The API must be initialized before it can be used.
 * Use {@link #initialize()} to start the asynchronous initialization process
 * and {@link #getInstanceFuture()} or {@link #getInstance()} to obtain a
 * ready-to-use {@code MCEFApi} instance.
 */
public interface MCEFApi {

    /**
     * Triggers MCEF Modern asynchronous initialization.
     * <p>
     * This method is thread-safe and may be called multiple times;
     * subsequent calls will return the same {@link Initialization} instance.
     * <p>
     * The returned {@link Initialization} object can be used to monitor
     * initialization progress or to obtain a {@link CompletableFuture}
     * that completes once initialization finishes successfully.
     *
     * @return the {@link Initialization} instance representing the initialization process
     */
    static Initialization initialize() {
        return MCEFApiImpl.initialize();
    }

    /**
     * Returns a {@link CompletableFuture} that completes when MCEF initialization
     * is finished and a usable {@link MCEFApi} instance is available.
     * <p>
     * Internally, this method ensures that initialization has been started.
     *
     * @return a future that will complete with the {@link MCEFApi} instance
     */
    static CompletableFuture<MCEFApi> getInstanceFuture() {
        return initialize().getFuture();
    }

    /**
     * Returns the {@link MCEFApi} instance once initialization is complete.
     * <p>
     * This method blocks the calling thread until initialization finishes.
     * <p>
     * If you prefer to avoid blocking, use {@link #getInstanceFuture()} instead.
     *
     * @return the initialized {@link MCEFApi} instance
     * @throws java.util.concurrent.CompletionException if initialization fails
     */
    static MCEFApi getInstance() {
        return getInstanceFuture().join();
    }

    /**
     * Creates a new browser instance managed by MCEF.
     *
     * @param url         the initial URL to load
     * @param transparent whether the browser should use a transparent background
     * @return a new {@link MCEFBrowser} instance
     */
    MCEFBrowser createBrowser(String url, boolean transparent);

    /**
     * Returns the shared {@link org.cef.CefClient} used by all browsers created
     * through this API instance. Allows modders to register additional handlers
     * (e.g. {@link org.cef.handler.CefRequestHandler}) on the client.
     *
     * @return the shared {@link org.cef.CefClient}
     */
    org.cef.CefClient getClient();

    /**
     * Represents the asynchronous initialization process of MCEF Modern.
     * <p>
     * Provides progress information and access to the resulting API instance.
     */
    interface Initialization {

        /**
         * Returns the current initialization stage.
         *
         * @return the current {@link Stage}
         */
        Initialization.Stage getStage();

        /**
         * Returns the approximate progress of the current initialization stage
         * as a value between {@code 0.0} and {@code 100.0} or the {@code -1.0},
         * if no percentage available
         *
         * @return the initialization progress percentage or -1.0
         */
        float getPercentage();

        /**
         * Returns a {@link CompletableFuture} that completes when initialization finishes
         * and provides the initialized {@link MCEFApi} instance.
         *
         * @return a future completing with the {@link MCEFApi} instance
         */
        CompletableFuture<MCEFApi> getFuture();

        /**
         * Checks if the initialization process is fully complete.
         *
         * @return {@code true} if initialization has finished, {@code false} otherwise
         */
        default boolean isDone() {
            return getStage() == Stage.DONE;
        }

        /**
         * Enumeration of possible initialization stages for MCEF Modern.
         */
        enum Stage {

            /**
             * Initialization has not yet started.
             */
            NOT_STARTED,

            /**
             * Downloading the native JCef bundle
             */
            DOWNLOADING,

            /**
             * Extracting the downloaded/located native bundle
             */
            EXTRACTING,

            /**
             * The installation process is being executed.
             */
            INSTALL,

            /**
             * Initializing for the corresponding platform.
             */
            INITIALIZING,

            /**
             * Initialization is complete and the API is ready to use.
             */
            DONE
        }

    }

}

