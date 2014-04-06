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

package org.andstatus.app.support.android.v11.app;

import android.os.Bundle;

import org.andstatus.app.util.MyLog;

/**
 * A temporary substitute for the {@link android.app.LoaderManager}
 */
public class MyLoaderManager<T> implements MyLoader.OnLoadCompleteListener<T>, MyLoader.OnLoadCanceledListener<T> {
    int id = 0;
    Bundle args = null;
    MyLoader<T> loader = null;
    MyLoader<T> prevLoader = null;
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
        MyLoader<T> newLoader = callback.onCreateLoader(id, args);
        if (loader != null && loader != newLoader) {
            destroyLoaderLater(id);
        }
        this.loader = newLoader;
        this.id = id;
        this.args = args;
        this.callback = callback;
        if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
            MyLog.v(this, "initLoader: " + newLoader);
        }
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

    private void destroyLoaderLater(int id) {
        destroyPrevLoader(id);
        prevLoader = loader;
        loader = null;
        if (prevLoader != null) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, "destroyLoaderLater: " + prevLoader);
            }
            prevLoader.unregisterListener(this);
            prevLoader.unregisterOnLoadCanceledListener(this);
            prevLoader.stopLoading();
        }
    }

    private void destroyPrevLoader(int id) {
        if (prevLoader != null) {
            if (MyLog.isLoggable(this, MyLog.VERBOSE)) {
                MyLog.v(this, "destroyPrevLoader: " + prevLoader);
            }
            prevLoader.reset();
        }
        prevLoader = null;
    }
    
    public void destroyLoader(int id) {
        destroyLoaderLater(id);
        destroyPrevLoader(id);
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
            destroyPrevLoader(id);
        }
    }
    
    @Override
    public void onLoadCanceled(MyLoader<T> loader) {
        MyLog.v(this, "onLoadCanceled");
        if (callback != null) {
            callback.onLoadFinished(loader, null);
            destroyPrevLoader(id);
        }
    }

    public void onResumeActivity(int id) {
        MyLoader<T> loader1 = getLoader(id);
        if (loader1 != null) {
            loader1.startLoading();
        }
    }

    public void onPauseActivity(int id) {
        MyLoader<T> loader1 = getLoader(id);
        if (loader1 != null) {
            loader1.stopLoading();
        }
    }
    
}
