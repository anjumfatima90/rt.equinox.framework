/*******************************************************************************
 * Copyright (c) 2013, 2014 IBM Corporation and others.
 *
 * This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License 2.0
 * which accompanies this distribution, and is available at
 * https://www.eclipse.org/legal/epl-2.0/
 *
 * SPDX-License-Identifier: EPL-2.0
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.osgi.tests.hooks.framework;

import java.io.IOException;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.Enumeration;
import java.util.Map;
import org.eclipse.core.runtime.adaptor.EclipseStarter;
import org.eclipse.core.tests.harness.CoreTest;
import org.eclipse.osgi.internal.hookregistry.HookRegistry;
import org.eclipse.osgi.launch.EquinoxFactory;
import org.eclipse.osgi.tests.OSGiTestsActivator;
import org.eclipse.osgi.tests.bundles.BundleInstaller;
import org.osgi.framework.BundleContext;
import org.osgi.framework.FrameworkEvent;
import org.osgi.framework.launch.Framework;
import org.osgi.framework.launch.FrameworkFactory;

public abstract class AbstractFrameworkHookTests extends CoreTest {
	protected static class BasicURLClassLoader extends URLClassLoader {
		public BasicURLClassLoader(URL[] urls, ClassLoader parent) {
			super(urls, parent);
		}

		@Override
		public URL getResource(String name) {
			if (isLocalResource(name))
				return findResource(name);
			return super.getResource(name);
		}

		@Override
		public void addURL(URL url) {
			super.addURL(url);
		}

		@Override
		public Enumeration<URL> getResources(String name) throws IOException {
			if (isLocalResource(name))
				return findResources(name);
			return super.getResources(name);
		}

		@Override
		protected synchronized Class<?> loadClass(String name, boolean resolve) throws ClassNotFoundException {
			if (name.startsWith("org.eclipse") || name.startsWith("org.osgi.framework.FrameworkUtil")) {
				Class<?> result = findLoadedClass(name);
				if (result == null)
					result = findClass(name);
				return result;
			}
			return super.loadClass(name, resolve);
		}

		private boolean isLocalResource(String name) {
			return name.startsWith("org/eclipse") || name.equals(HookRegistry.HOOK_CONFIGURATORS_FILE);
		}
	}

	protected static final String BUNDLES_ROOT = "bundle_tests";

	protected BasicURLClassLoader classLoader;
	protected BundleInstaller bundleInstaller;

	public BundleContext getContext() {
		return OSGiTestsActivator.getContext();
	}

	protected void assertBundleDiscarded(String location, Framework framework) {
		assertNull("Bundle " + location + " was not discarded", framework.getBundleContext().getBundle(location));
	}

	protected void assertBundleNotDiscarded(String location, Framework framework) {
		assertNotNull("Bundle " + location + " was discarded", framework.getBundleContext().getBundle(location));
	}

	protected Framework createFramework(Map<String, String> configuration) throws Exception {
		FrameworkFactory factory = (FrameworkFactory) classLoader.loadClass(EquinoxFactory.class.getName()).newInstance();
		Framework framework = factory.newFramework(configuration);
		return framework;
	}

	protected void initAndStart(Framework framework) throws Exception {
		framework.init();
		framework.start();
	}

	protected Framework restart(Framework framework, Map<String, String> configuration) throws Exception {
		stop(framework);
		framework = createFramework(configuration);
		initAndStart(framework);
		return framework;
	}

	protected void setUp() throws Exception {
		setUpBundleInstaller();
		setUpClassLoader();
	}

	protected void stop(Framework framework) throws Exception {
		stop(framework, false);
	}

	protected void stopQuietly(Framework framework) {
		if (framework == null)
			return;
		try {
			stop(framework, true);
		} catch (Exception e) {
			// ignore;
		}
	}

	private void stop(Framework framework, boolean quietly) throws Exception {
		try {
			framework.stop();
			FrameworkEvent event = framework.waitForStop(10000);
			if (!quietly) {
				assertEquals("The framework was not stopped", FrameworkEvent.STOPPED, event.getType());
			}
		} catch (Exception e) {
			if (!quietly) {
				throw e;
			}
		}
	}

	protected void tearDown() throws Exception {
		bundleInstaller.shutdown();
	}

	private void setUpBundleInstaller() throws Exception {
		bundleInstaller = new BundleInstaller(BUNDLES_ROOT, getContext());
	}

	private void setUpClassLoader() throws Exception {
		BundleContext context = getContext();
		String osgiFramework = context.getProperty(EclipseStarter.PROP_FRAMEWORK);
		URL[] urls;
		if ("folder".equals(context.getProperty(EclipseStarter.PROP_FRAMEWORK_SHAPE)))
			urls = new URL[] {new URL(osgiFramework), new URL(osgiFramework + "bin/")};
		else
			urls = new URL[] {new URL(osgiFramework)};
		classLoader = new BasicURLClassLoader(urls, getClass().getClassLoader());
	}
}
