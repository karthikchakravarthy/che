/*******************************************************************************
 * Copyright (c) 2012-2017 Codenvy, S.A.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *   Codenvy, S.A. - initial API and implementation
 *******************************************************************************/
package org.eclipse.che.ide.bootstrap;

import com.google.gwt.event.shared.EventHandler;
import com.google.gwt.event.shared.GwtEvent;

import org.eclipse.che.ide.api.app.AppContext;

/**
 * Fired when essential initialization routines of the IDE application have been successfully done.
 * In other words, when {@link AppContext} is already initialized with the current workspace's data.
 * <p>
 * <b>NOTE:</b> for internal using by the Basic IDE components.
 */
public class IdeInitializedEvent extends GwtEvent<IdeInitializedEvent.Handler> {

    public static final Type<IdeInitializedEvent.Handler> TYPE = new Type<>();

    @Override
    public Type<Handler> getAssociatedType() {
        return TYPE;
    }

    @Override
    protected void dispatch(Handler handler) {
        handler.onIdeInitializedEvent(this);
    }

    public interface Handler extends EventHandler {
        void onIdeInitializedEvent(IdeInitializedEvent event);
    }
}
