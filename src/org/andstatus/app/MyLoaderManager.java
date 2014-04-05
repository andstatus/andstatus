/**
 * Copyright (C) 2014 yvolk (Yuri Volkov), http://yurivolkov.com
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.andstatus.app;

import android.os.Bundle;

import org.andstatus.app.util.MyLog;

/**
 * A temporary substitute for the {@link android.app.LoaderManager}
 */
public class MyLoaderManager<T> implements MyLoader.OnLoadCompleteListener<T>, MyLoader.OnLoadCanceledListener<T> {
    int id = 0;
    Bundle args = null;
    MyLoader<T> loader = null;
    LoaderCallbacks<T> callback = null;
    
    /**
     * A temporary simplified substitute for the {@link android.app.LoaderManager.LoaderCallbacks}
     */
    public interface LoaderCallbacks<D> {
            /**
             * {@link android.app.LoaderManager.LoaderCallbacks#onCreateLoader(int, Bundle)}
             */
            public MyLoader<D> onCreateLoader(int id, Bundle args);
            /**
             * {@link android.app.LoaderManager.LoaderCallbacks#onLoadFinished(android.content.Loader, Object)}
             */
            public void onLoadFinished(MyLoader<D> loader, D data);
            /**
             * {@link android.app.LoaderManager.LoaderCallbacks#onLoaderReset(android.content.Loader)}
             */
            public void onLoaderReset(MyLoader<D> loader);
    }
    
    /** See {@link android.app.LoaderManager#initLoader} */
    public MyLoader<T> initLoader(int id, Bundle args,
            LoaderCallbacks<T> callback) {
        if (this.loader != null) {
            // Maybe we need to wait for cancellation, maybe not... Here we don't wait.
            destroyLoader(id);
        }
        this.id = id;
        this.args = args;
        this.callback = callback;
        this.loader = callback.onCreateLoader(id, args);
        loader.registerListener(id, this);
        loader.registerOnLoadCanceledListener(this);
        loader.startLoading();
        return null;
    }
    
    /** See {@link android.app.LoaderManager#restartLoader} */
    public MyLoader<T> restartLoader(int id, Bundle args,
            LoaderCallbacks<T> callback) {
        return initLoader(id, args, callback);
    }

    public void destroyLoader(int id) {
        if (loader != null) {
            loader.reset();
        }
        loader = null;
    }

    /**
     * Return the Loader with the given id or null if no matching Loader
     * is found.
     */
    public MyLoader<T> getLoader(int id) {
        return loader;
    }

    @Override
    public void onLoadComplete(MyLoader<T> loader, T data) {
        MyLog.v(this, "onLoadComplete");
        if (callback != null) {
            callback.onLoadFinished(loader, data);
        }
    }
    
    @Override
    public void onLoadCanceled(MyLoader<T> loader) {
        MyLog.v(this, "onLoadCanceled");
        if (callback != null) {
            callback.onLoadFinished(loader, null);
        }
    }
    
}
