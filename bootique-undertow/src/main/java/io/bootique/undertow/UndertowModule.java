/**
 *  Licensed to ObjectStyle LLC under one
 *  or more contributor license agreements.  See the NOTICE file
 *  distributed with this work for additional information
 *  regarding copyright ownership.  The ObjectStyle LLC licenses
 *  this file to you under the Apache License, Version 2.0 (the
 *  “License”); you may not use this file except in compliance
 *  with the License.  You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing,
 *  software distributed under the License is distributed on an
 *  “AS IS” BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 *  KIND, either express or implied.  See the License for the
 *  specific language governing permissions and limitations
 *  under the License.
 */

package io.bootique.undertow;

import com.google.inject.Binder;
import com.google.inject.Key;
import com.google.inject.Provides;
import com.google.inject.Singleton;
import com.google.inject.multibindings.OptionalBinder;
import io.bootique.BQCoreModule;
import io.bootique.BootiqueException;
import io.bootique.ConfigModule;
import io.bootique.config.ConfigurationFactory;
import io.bootique.log.BootLogger;
import io.bootique.shutdown.ShutdownManager;
import io.bootique.undertow.command.ServerCommand;
import io.bootique.undertow.handlers.RootHandler;
import io.undertow.server.HttpHandler;

import javax.net.ssl.SSLContext;
import java.util.Optional;

import static io.undertow.Undertow.Builder;
import static io.undertow.Undertow.builder;

/**
 * @since 0.1
 */
public class UndertowModule extends ConfigModule {

    @Override
    public void configure(Binder binder) {
        BQCoreModule.extend(binder).addCommand(ServerCommand.class);
        OptionalBinder.newOptionalBinder(binder, Key.get(HttpHandler.class, RootHandler.class));
    }

    @Provides
    @Singleton
    public UndertowFactory undertowFactory(ConfigurationFactory configurationFactory) {
        return configurationFactory.config(UndertowFactory.class, configPrefix);
    }

    @Provides
    @Singleton
    public UndertowServer createServer(
            Builder builder,
            BootLogger bootLogger,
            ShutdownManager shutdownManager
    ) {
        UndertowServer server = new UndertowServer(builder);

        shutdownManager.addShutdownHook(() -> {
            bootLogger.trace(() -> "stopping Undertow...");
            server.stop();
        });

        return server;
    }

    @Singleton
    @Provides
    public Builder createBuilder(
            UndertowFactory undertowFactory,
            @RootHandler Optional<HttpHandler> httpHandler
    ) {
        final Builder builder = builder();

        undertowFactory.getHttpListeners()
                .forEach(listener ->
                        builder.addHttpListener(
                                listener.getPort(),
                                listener.getHost()
                        )
                );

        undertowFactory.getHttpsListeners()
                .forEach(listener ->
                        builder.addHttpsListener(
                                listener.getPort(),
                                listener.getHost(),
                                createSslContext(undertowFactory)
                        )
                );

        if (undertowFactory.getBufferSize() != null) {
            builder.setBufferSize(undertowFactory.getBufferSize());
        }

        if (undertowFactory.getIoThreads() != null) {
            builder.setIoThreads(undertowFactory.getIoThreads());
        }

        if (undertowFactory.getWorkerThreads() != null) {
            builder.setWorkerThreads(undertowFactory.getWorkerThreads());
        }

        if (undertowFactory.getDirectBuffers() != null) {
            builder.setDirectBuffers(undertowFactory.getDirectBuffers());
        }

        httpHandler
            .map(builder::setHandler)
            .orElseThrow(this::noHttpHandlerException);

        return builder;
    }

    private BootiqueException noHttpHandlerException() {
        return new BootiqueException(1,
            "Module Bootique-Undertow excepts user binding of type io.undertow.server.HttpHandler " +
                "marked with io.bootique.undertow.handlers.RootHandler annotation."
        );
    }

    private SSLContext createSslContext(UndertowFactory config) {
        return null;
    }
}
