package com.contentful.java.api;

import com.contentful.java.model.*;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import retrofit.ErrorHandler;
import retrofit.RequestInterceptor;
import retrofit.RestAdapter;
import retrofit.client.Client;
import retrofit.converter.GsonConverter;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import static com.contentful.java.lib.Constants.*;

/**
 * Client to be used when requesting information from the server.
 * <p>Every client is associated with exactly one Space, but there is no limit to the concurrent number
 * of clients existing at any one time.</p>
 *
 * @see Builder for instructions of how to create a client.
 */
@SuppressWarnings("UnusedDeclaration")
public class CDAClient {
    // Definitions & Configuration
    static final String USER_AGENT = String.format("contentful.java/%s", VERSION_NAME);

    private String accessToken;
    private String spaceKey;

    // Members
    private CDAService service;
    private HashMap<String, Class<?>> customTypesMap;
    private Client.Provider clientProvider;
    private CDASpace space;

    // Gson
    private Gson gson;

    // Executors
    ExecutorService executorService;

    private CDAClient() {
    }

    /**
     * Initialization method - should be called once all configuration properties are set.
     */
    private void init(Builder builder) {
        // Initialize members
        customTypesMap = new HashMap<String, Class<?>>();
        this.spaceKey = builder.spaceKey;
        this.accessToken = builder.accessToken;

        // Initialize Gson
        initGson();

        // Create a service
        RestAdapter.Builder restBuilder = new RestAdapter.Builder()
                .setEndpoint(CDA_SERVER_URI)
                .setConverter(new GsonConverter(gson))
                .setRequestInterceptor(getRequestInterceptor());

        if (builder.clientProvider != null) {
            restBuilder.setClient(builder.clientProvider);
        }

        if (builder.errorHandler != null) {
            restBuilder.setErrorHandler(builder.errorHandler);
        }

        service = restBuilder.build().create(CDAService.class);

        // Init ExecutorService (will be used for parsing of array results and spaces synchronization).
        executorService = Executors.newCachedThreadPool(new ThreadFactory() {
            @Override
            public Thread newThread(final Runnable r) {
                return new Thread(new Runnable() {
                    @Override
                    public void run() {
                        Thread.currentThread().setPriority(Thread.MIN_PRIORITY);
                        r.run();
                    }
                }, IDLE_THREAD_NAME);
            }
        });
    }

    /**
     * Initialize Gson instance.
     */
    private void initGson() {
        Gson arrayGson = setBaseTypeAdapters(new GsonBuilder(), this).create();

        gson = setBaseTypeAdapters(new GsonBuilder(), this)
                .registerTypeAdapter(CDAArray.class, new ArrayResourceTypeAdapter(CDAClient.this, arrayGson))
                .registerTypeAdapter(CDASyncedSpace.class, new ArrayResourceTypeAdapter(CDAClient.this, arrayGson))
                .create();
    }

    static GsonBuilder setBaseTypeAdapters(GsonBuilder gsonBuilder, CDAClient client) {
        return gsonBuilder
                .registerTypeAdapter(CDAResource.class, new ResourceTypeAdapter(client))
                .registerTypeAdapter(CDAEntry.class, new ResourceTypeAdapter(client))
                .registerTypeAdapter(CDAAsset.class, new ResourceTypeAdapter(client))
                .registerTypeAdapter(CDAContentType.class, new ResourceTypeAdapter(client));
    }

    /**
     * Returns a {@link retrofit.RequestInterceptor} instance.
     * This ensures requests will include authentication headers following
     * the standardized OAuth 2.0 Bearer Token Specification as per the Content Delivery API.
     */
    private RequestInterceptor getRequestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void intercept(RequestFacade requestFacade) {
                if (accessToken != null && !accessToken.isEmpty()) {
                    requestFacade.addHeader(HTTP_HEADER_AUTH,
                            String.format(HTTP_OAUTH_PATTERN, accessToken));
                }

                requestFacade.addHeader(HTTP_HEADER_USER_AGENT, USER_AGENT);
            }
        };
    }

    /**
     * Use this method in order to register custom {@link CDAEntry} subclasses to be instantiated by this client
     * when Entries of a specific Content Type are retrieved from the server.
     *
     * This allows the integration of custom value objects with convenience accessors, additional
     * conversions or custom functionality so that you can easily build your data model upon Entries.
     *
     * @param contentTypeIdentifier String representing a specific Content Type UID.
     * @param clazz                 Class type to instantiate when creating objects of
     *                              the specified Content Type (i.e. "SomeCustomEntry.class").
     */
    public void registerCustomClass(String contentTypeIdentifier, Class<?> clazz) {
        customTypesMap.put(contentTypeIdentifier, clazz);
    }

    /**
     * Get a mapping of Content Type UIDs to custom class types as registered
     * using the {@link #registerCustomClass} method.
     *
     * @return Map instance.
     */
    HashMap<String, Class<?>> getCustomTypesMap() {
        return customTypesMap;
    }

    /**
     * Fetch Assets.
     *
     * @param callback {@link CDACallback} instance.
     */
    public void fetchAssets(CDACallback<CDAArray> callback) {
        fetchArrayWithPathSegment(PATH_ASSETS, null, callback);
    }

    /**
     * Fetch Assets. (BLOCKING)
     *
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAArray fetchAssetsBlocking() throws Exception {
        return fetchArrayWithPathSegmentBlocking(PATH_ASSETS, null);
    }

    /**
     * Fetch Assets matching a specific query.
     *
     * @param query    Map representing the query.
     * @param callback {@link CDACallback} instance.
     */
    public void fetchAssetsMatching(Map<String, String> query, CDACallback<CDAArray> callback) {
        fetchArrayWithPathSegment(PATH_ASSETS, query, callback);
    }

    /**
     * Fetch Assets matching a specific query. (BLOCKING)
     *
     * @param query Map representing the query.
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAArray fetchAssetsMatchingBlocking(Map<String, String> query) throws Exception {
        return fetchArrayWithPathSegmentBlocking(PATH_ASSETS, query);
    }

    /**
     * Fetch a single Asset with identifier.
     *
     * @param identifier {@link java.lang.String} representing the Asset UID.
     * @param callback   {@link CDACallback} instance.
     */
    public void fetchAssetWithIdentifier(final String identifier, final CDACallback<CDAAsset> callback) {
        ensureSpace(new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchAssetWithIdentifier(CDAClient.this.spaceKey, identifier, callback);
            }
        });
    }

    /**
     * Fetch a single Asset with identifier. (BLOCKING)
     *
     * @param identifier {@link java.lang.String} representing the Asset UID.
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAAsset fetchAssetWithIdentifierBlocking(String identifier) throws Exception {
        if (ensureSpaceBlocking(false)) {
            return service.fetchAssetWithIdentifierBlocking(spaceKey, identifier);
        }

        return null; // todo throw exception and pass to custom error handler if there is one
    }

    /**
     * Fetch Entries.
     *
     * @param callback {@link CDACallback} instance.
     */
    public void fetchEntries(CDACallback<CDAArray> callback) {
        fetchArrayWithPathSegment(PATH_ENTRIES, null, callback);
    }

    /**
     * Fetch Entries. (BLOCKING)
     *
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAArray fetchEntriesBlocking() throws Exception {
        return fetchArrayWithPathSegmentBlocking(PATH_ENTRIES, null);
    }

    /**
     * Fetch Entries matching a specific query.
     *
     * @param query    Map representing the query.
     * @param callback {@link CDACallback} instance.
     */
    public void fetchEntriesMatching(Map<String, String> query, CDACallback<CDAArray> callback) {
        fetchArrayWithPathSegment(PATH_ENTRIES, query, callback);
    }

    /**
     * Fetch Entries matching a specific query. (BLOCKING)
     *
     * @param query Map representing the query.
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAArray fetchEntriesMatchingBlocking(Map<String, String> query) throws Exception {
        return fetchArrayWithPathSegmentBlocking(PATH_ENTRIES, query);
    }

    /**
     * Fetch a single Entry with identifier.
     *
     * When expecting result of a custom type which was previously registered using the {@link #registerCustomClass}
     * method, the type of the expected object can also be specified as the generic type of the
     * {@link CDACallback} instance (i.e. {@literal "new CDACallback<SomeCustomClass>(){...}"}).
     *
     * @param identifier String representing the UID of the Entry.
     * @param callback   {@link CDACallback} instance.
     */
    public void fetchEntryWithIdentifier(final String identifier, final CDACallback<? extends CDAEntry> callback) {
        ensureSpace(new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchEntryWithIdentifier(CDAClient.this.spaceKey, identifier, callback);
            }
        });
    }

    /**
     * Fetch a single Entry with identifier.
     *
     * @param identifier String representing the UID of the Entry.
     * @return {@link CDAEntry} or a subclass of it.
     * @throws Exception in case of an error.
     * @see #fetchEntryWithIdentifier(String, CDACallback)
     */
    @SuppressWarnings("unchecked")
    public CDAEntry fetchEntryWithIdentifierBlocking(String identifier) throws Exception {
        if (ensureSpaceBlocking(false)) {
            return service.fetchEntryWithIdentifierBlocking(spaceKey, identifier);
        }

        return null; // todo throw exception and pass to custom error handler if there is one
    }

    /**
     * Fetch all Content Types from a Space.
     *
     * @param callback {@link CDACallback} instance.
     */
    public void fetchContentTypes(final CDACallback<CDAArray> callback) {
        ensureSpace(new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchContentTypes(CDAClient.this.spaceKey, callback);
            }
        });
    }

    /**
     * Fetch all Content Types from a Space. (BLOCKING)
     *
     * @return {@link CDAArray} result.
     * @throws Exception in case of an error.
     */
    public CDAArray fetchContentTypesBlocking() throws Exception {
        if (ensureSpaceBlocking(false)) {
            return service.fetchContentTypesBlocking(spaceKey);
        }

        return null; // todo throw exception and pass to custom error handler if there is one
    }

    /**
     * Fetch a single Content Type with identifier.
     *
     * @param identifier String representing the Content Type UID.
     * @param callback   {@link CDACallback} instance.
     */
    public void fetchContentTypeWithIdentifier(final String identifier, final CDACallback<CDAContentType> callback) {
        ensureSpace(new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchContentTypeWithIdentifier(CDAClient.this.spaceKey, identifier, callback);
            }
        });
    }

    /**
     * Fetch a single Content Type with identifier. (BLOCKING)
     *
     * @param identifier String representing the Content Type UID.
     * @return {@link CDAContentType} result.
     * @throws Exception
     */
    public CDAContentType fetchContentTypeWithIdentifierBlocking(String identifier) throws Exception {
        if (ensureSpaceBlocking(false)) {
            return service.fetchContentTypeWithIdentifierBlocking(spaceKey, identifier);
        }

        return null; // todo throw exception and pass to custom error handler if there is one
    }

    /**
     * Fetch any kind of Resource from the server.
     * This method can be used in cases where the actual type of Resource to be fetched is determined at runtime.
     *
     * Allowed Resource types are:
     * <ul>
     * <li>{@link com.contentful.java.lib.Constants.CDAResourceType#Asset}</li>
     * <li>{@link com.contentful.java.lib.Constants.CDAResourceType#ContentType}</li>
     * <li>{@link com.contentful.java.lib.Constants.CDAResourceType#Entry}</li>
     * </ul>
     *
     * Note: This method <b>will throw an {@link java.lang.IllegalArgumentException}</b> in cases where an
     * invalid resource type is specified.
     *
     * @param resourceType The type of Resource to be fetched.
     * @param callback     {@link CDACallback} instance.
     */
    public void fetchResourcesOfType(CDAResourceType resourceType, CDACallback<CDAArray> callback) {
        if (CDAResourceType.Asset.equals(resourceType)) {
            fetchAssets(callback);
        } else if (CDAResourceType.ContentType.equals(resourceType)) {
            fetchContentTypes(callback);
        } else if (CDAResourceType.Entry.equals(resourceType)) {
            fetchEntries(callback);
        } else {
            throw new IllegalArgumentException("Invalid resource type, allowed types are: Asset, ContentType, Entry.");
        }
    }

    /**
     * An extension of {@link #fetchResourcesOfType} method.
     * Allowed Resource types are:
     * <ul>
     * <li>{@link com.contentful.java.lib.Constants.CDAResourceType#Asset}</li>
     * <li>{@link com.contentful.java.lib.Constants.CDAResourceType#Entry}</li>
     * </ul>
     *
     * Note: This method <b>will throw an {@link java.lang.IllegalArgumentException}</b> in cases where an
     * invalid resource type is specified.
     *
     * @param resourceType The type of Resource to be fetched.
     * @param query        {@link java.util.Map} representing the query.
     * @param callback     {@link CDACallback} instance.
     */
    public void fetchResourcesOfTypeMatching(CDAResourceType resourceType,
                                             Map<String, String> query,
                                             CDACallback<CDAArray> callback) {

        if (CDAResourceType.Asset.equals(resourceType)) {
            fetchAssetsMatching(query, callback);
        } else if (CDAResourceType.Entry.equals(resourceType)) {
            fetchEntriesMatching(query, callback);
        } else {
            throw new IllegalArgumentException("Invalid resource type, allowed types are: Asset, Entry.");
        }
    }

    /**
     * Fetch a single Space.
     *
     * @param callback {@link CDACallback} instance.
     */
    public void fetchSpace(CDACallback<CDASpace> callback) {
        service.fetchSpace(this.spaceKey, callback);
    }

    /**
     * Fetch a single Space. (BLOCKING)
     *
     * @return {@link CDASpace} result.
     */
    public CDASpace fetchSpaceBlocking() throws Exception {
        return service.fetchSpaceBlocking(this.spaceKey);
    }

    private void fetchArrayWithPathSegment(final String pathSegment,
                                           final Map<String, String> query,
                                           final CDACallback<CDAArray> callback) {

        ensureSpace(new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchArrayWithPath(CDAClient.this.spaceKey,
                        pathSegment,
                        query,
                        callback);
            }
        });
    }

    private CDAArray fetchArrayWithPathSegmentBlocking(String pathSegment, Map<String, String> query) throws Exception {
        if (ensureSpaceBlocking(false)) {
            return service.fetchArrayWithPathBlocking(spaceKey, pathSegment, query);
        }

        return null; // todo throw exception and pass to custom error handler if there is one
    }

    private void ensureSpace(EnsureSpaceCallback callback) {
        ensureSpace(false, callback);
    }

    private void ensureSpace(boolean invalidate, final EnsureSpaceCallback callback) {
        if (invalidate || space == null) {
            fetchSpace(callback);
        } else {
            callback.onSuccess(space, null);
        }
    }

    private boolean ensureSpaceBlocking(boolean invalidate) throws Exception {
        if (invalidate || space == null) {
            space = fetchSpaceBlocking();
        }

        return space != null;
    }

    /**
     * Initial sync for a Space.
     *
     * @param callback {@link CDACallback} instance.
     */
    public void performInitialSynchronization(final CDACallback<CDASyncedSpace> callback) {
        ensureSpace(true, new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.performSynchronization(spaceKey, true,
                        new SyncSpaceCallback(null, CDAClient.this, callback));
            }
        });
    }

    // TBD
    public void performSynchronization(final CDASyncedSpace existingSpace, final CDACallback<CDASyncedSpace> callback) {
        ensureSpace(true, new EnsureSpaceCallback(this, callback) {
            @Override
            void onSpaceReady() {
                service.fetchSyncedSpaceWithPath(existingSpace.getNextSyncUrl(),
                        new SyncSpaceCallback(existingSpace, CDAClient.this, callback));
            }
        });
    }

/*
    TBD
    public void fetchNextItemsFromList(CDAArray previousResult, CDACallback<CDAArray> callback) {
        HashMap<String, String> map = Utils.getNextBatchQueryMapForList(previousResult);

        if (map == null) {
            return;
        }

        service.fetchEntriesMatching(this.spaceKey, map, callback);
    }
*/

    public CDASpace getSpace() {
        return this.space;
    }

    void onSpaceReady(CDASpace space) {
        if (space != null && this.space != space) {
            this.space = space;
        }
    }

    /**
     * Build a new {@link CDAClient}.
     *
     * Calling the following methods is required before calling {@link #build}:
     * <ul>
     * <li>{@link #setSpaceKey(String)}</li>
     * <li>{@link #setAccessToken(String)}</li>
     * </ul>
     */
    public static class Builder {
        private String spaceKey;
        private String accessToken;
        private Client.Provider clientProvider;
        private ErrorHandler errorHandler;

        /**
         * Sets the space key to be used with this client.
         *
         * @param spaceKey String representing the space key.
         * @return this {@code Builder} instance.
         */
        public Builder setSpaceKey(String spaceKey) {
            if (spaceKey == null) {
                throw new NullPointerException("Space key may not be null.");
            }

            this.spaceKey = spaceKey;
            return this;
        }

        /**
         * Sets the access token to be used with this client.
         *
         * @param accessToken String representing access token to be used when authenticating against the CDA.
         * @return this {@code Builder} instance.
         */
        public Builder setAccessToken(String accessToken) {
            if (accessToken == null) {
                throw new NullPointerException("Access token may not be null.");
            }

            this.accessToken = accessToken;
            return this;
        }

        /**
         * Sets a custom client to be used for making HTTP requests.
         *
         * @param client {@link retrofit.client.Client} instance.
         * @return this {@code Builder} instance.
         */
        public Builder setClient(final Client client) {
            if (client == null) {
                throw new NullPointerException("Client may not be null.");
            }

            return setClient(new Client.Provider() {
                @Override
                public Client get() {
                    return client;
                }
            });
        }

        /**
         * Sets a provider of clients to be used for making HTTP requests.
         *
         * @param clientProvider {@link retrofit.client.Client.Provider} instance.
         * @return this {@code Builder} instance.
         */
        public Builder setClient(Client.Provider clientProvider) {
            if (clientProvider == null) {
                throw new NullPointerException("Client provider may not be null.");
            }

            this.clientProvider = clientProvider;
            return this;
        }

        /**
         * The error handler allows you to customize the type of exception thrown for errors on synchronous requests.
         *
         * @param errorHandler Error handler to use.
         * @return this {@code Builder} instance.
         */
        public Builder setErrorHandler(ErrorHandler errorHandler) {
            if (errorHandler == null) {
                throw new NullPointerException("Error handler may not be null.");
            }

            this.errorHandler = errorHandler;
            return this;
        }

        /**
         * Builds and returns a {@link CDAClient}.
         *
         * @return Client instance.
         */
        public CDAClient build() {
            CDAClient client = new CDAClient();
            client.init(this);

            return client;
        }
    }
}