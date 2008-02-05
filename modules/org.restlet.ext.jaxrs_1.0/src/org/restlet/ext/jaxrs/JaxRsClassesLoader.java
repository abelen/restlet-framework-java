package org.restlet.ext.jaxrs;

import java.io.IOException;
import java.lang.reflect.Modifier;
import java.util.Iterator;
import java.util.logging.Level;

import javax.ws.rs.Path;
import javax.ws.rs.ext.MessageBodyReader;
import javax.ws.rs.ext.MessageBodyWriter;
import javax.ws.rs.ext.Provider;

import org.restlet.ext.jaxrs.util.ClasspathIterator;
import org.restlet.ext.jaxrs.util.PackageIterator;
import org.restlet.ext.jaxrs.util.ServiceProviderIterator;
import org.restlet.ext.jaxrs.util.WrappedClassLoadException;
import org.restlet.ext.jaxrs.util.WrappedLoadException;

/**
 * This class loads the root resource classes and the {@link Provider}s. If the
 * automatic loading of the {@link Provider}s doesn't work, check
 * {@link #loadProvidersFromFile(ClassLoader, boolean, JaxRsRouter)}.
 * 
 * This class has is NOT a java {@link ClassLoader}, also if the name may
 * suggest it a little bit.
 * 
 * @author Stephan Koops
 */
class JaxRsClassesLoader {

    /**
     * Adds the class to the given router.
     * 
     * @param clazz
     *                the class to add
     * @param jaxRsRouter
     *                the {@link JaxRsRouter} to add the class to.
     * @param asRootResourceClass
     *                if true, this class is checked, if it is a root resource
     *                class, otherwise not.
     * @param asProvider
     *                if true, this class is checked, if it is a provider.
     * @throws IllegalArgumentException
     */
    private static boolean addClassToRouter(Class<?> clazz,
            JaxRsRouter jaxRsRouter, boolean asRootResourceClass,
            boolean asProvider) throws IllegalArgumentException {
        boolean result = false;
        int modifiers = clazz.getModifiers();
        if(Modifier.isAbstract(modifiers) || Modifier.isInterface(modifiers))
            return false;
        if (asProvider) {
            if (MessageBodyReader.class.isAssignableFrom(clazz)) {
                if (!clazz.isAnnotationPresent(Provider.class)) {
                    String msg = "The class "
                            + clazz.getName()
                            + " implements the MessageBodyReader, but is not annotated with @Provider. Will although use it as MessageBodyReader.";
                    jaxRsRouter.getLogger().log(Level.INFO, msg);
                }
                jaxRsRouter.addMessageBodyReader(clazz);
                result = true;
            }
            if (MessageBodyWriter.class.isAssignableFrom(clazz)) {
                if (!clazz.isAnnotationPresent(Provider.class)) {
                    String msg = "The class "
                            + clazz.getName()
                            + " implements the MessageBodyWriter, but is not annotated with @Provider. Will although use it as MessageBodyWriter.";
                    jaxRsRouter.getLogger().log(Level.INFO, msg);
                }
                jaxRsRouter.addMessageBodyWriter(clazz);
                result = true;
            } else if (!result && clazz.isAnnotationPresent(Provider.class)) {
                String msg = "The class "
                        + clazz.getName()
                        + " is annotated with @Provider, but does nor implement MessageBodyWriter nor MessageBodyReader. Don't know how to use it.";
                jaxRsRouter.getLogger().log(Level.INFO, msg);
            }
        }
        if (asRootResourceClass) {
            if (clazz.isAnnotationPresent(Path.class)) {
                jaxRsRouter.attach(clazz);
                result = true;
            }
        }
        return result;
    }

    /**
     * Loads the root resource classes and the providers from the classpath.
     * 
     * @param jaxRsRouter
     *                The {@link JaxRsRouter} to add the root resource classes
     *                and providers.
     * @param rootResourceClasses
     *                if the root resource classes should be loaded, set to
     *                true, otherwise to false.
     * @param providers
     *                if the providers should be loaded, set to true, otherwise
     *                to false.
     * @see #loadFromClasspath(JaxRsRouter)
     */
    static void loadFromClasspath(JaxRsRouter jaxRsRouter,
            boolean rootResourceClasses, boolean providers) {
        Iterator<Class<?>> cpIter = new ClasspathIterator();
        while (cpIter.hasNext()) {
            Class<?> clazz = cpIter.next();
            addClassToRouter(clazz, jaxRsRouter, rootResourceClasses, providers);
        }
    }

    /**
     * If the automatic loading of the {@link Provider}s doesn't work, you can
     * use this method to load the providers, that's class names are listed in
     * the files META-INF/services/javax.ws.rs.ext.MessageBodyWriter and
     * META-INF/services/javax.ws.rs.ext.MessageBodyWriter reachable with the
     * given {@link ClassLoader}.
     * 
     * Loads all Providers that's class names are listed in the files
     * META-INF/services/javax.ws.rs.ext.MessageBodyWriter and/or
     * META-INF/services/javax.ws.rs.ext.MessageBodyWriter reachable with the
     * given {@link ClassLoader}.
     * 
     * @see <a
     *      href="http://java.sun.com/javase/6/docs/technotes/guides/jar/jar.html#Service%20Provider">hslf</a>
     * 
     * @param classLoader
     * @param throwOnException
     * @param jaxRsRouter
     *                the {@link JaxRsRouter} to add the Providers to.
     * @throws IllegalArgumentException
     * @throws IOException
     * @throws ClassNotFoundException
     */
    static void loadProvidersFromFile(ClassLoader classLoader,
            boolean throwOnException, JaxRsRouter jaxRsRouter)
            throws IllegalArgumentException, IOException,
            ClassNotFoundException {
        try {
            @SuppressWarnings("unchecked")
            Iterator<Class<MessageBodyWriter<?>>> rIter = new ServiceProviderIterator(
                    MessageBodyWriter.class, throwOnException, classLoader,
                    jaxRsRouter.getLogger(), Level.WARNING);
            while (rIter.hasNext()) {
                Class<javax.ws.rs.ext.MessageBodyWriter<?>> clazz = rIter
                        .next();
                jaxRsRouter.addMessageBodyWriter(clazz);
            }
            @SuppressWarnings("unchecked")
            Iterator<Class<MessageBodyReader<?>>> wIter = new ServiceProviderIterator(
                    MessageBodyReader.class, throwOnException, classLoader,
                    jaxRsRouter.getLogger(), Level.WARNING);
            while (wIter.hasNext()) {
                Class<javax.ws.rs.ext.MessageBodyReader<?>> clazz = wIter
                        .next();
                jaxRsRouter.addMessageBodyReader(clazz);
            }
        } catch (WrappedClassLoadException e) {
            Throwable cause = e.getCause();
            if (cause instanceof Error)
                throw (Error) cause;
            if (cause instanceof RuntimeException)
                throw (RuntimeException) cause;
            if (cause instanceof ClassNotFoundException)
                throw (ClassNotFoundException) cause;
            if (cause instanceof IOException)
                throw (IOException) cause;
            throw e;
        }
    }

    /**
     * Load the classes in the package with the given name.
     * 
     * @param classLoader
     * @param throwOnExc
     * @param jaxRsRouter
     * @param rootResourceClasses
     *                if true, the classes are checked, if they are root
     *                resource classes.
     * @param providers
     *                if true, the classes are checked, if they are providers,
     *                see {@link Provider}
     * @param packageNames
     *                The packages to load
     * @throws WrappedLoadException
     *                 if the package could not be loaded, independent of
     *                 throwOnExc
     * @throws WrappedClassLoadException
     *                 if throwOnExc is true and a class could not be loaded.
     */
    static void loadFromPackage(ClassLoader classLoader, boolean throwOnExc,
            JaxRsRouter jaxRsRouter, boolean rootResourceClasses,
            boolean providers, String... packageNames)
            throws WrappedLoadException, WrappedClassLoadException {
        if (!rootResourceClasses && !providers)
            return;
        for (String packageName : packageNames) {
            Iterator<Class<?>> classIter;
            try {
                classIter = new PackageIterator(classLoader, packageName,
                        throwOnExc, jaxRsRouter.getLogger(), Level.WARNING);
            } catch (IOException e) {
                throw new WrappedLoadException(
                        "Could not load the package data", e);
            }
            while (classIter.hasNext()) {
                Class<?> clazz = classIter.next();
                addClassToRouter(clazz, jaxRsRouter, rootResourceClasses,
                        providers);
            }
        }
    }
}