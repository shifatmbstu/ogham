package fr.sii.ogham.core.resource.resolver;

import java.io.IOException;
import java.io.InputStream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import fr.sii.ogham.core.exception.resource.ResourceResolutionException;
import fr.sii.ogham.core.resource.ByteResource;
import fr.sii.ogham.core.resource.Resource;

/**
 * Resource resolver that searches for the resource into the classpath. This
 * implementation is able to manage path starting or not with '/'. The resource
 * resolution needs an absolute class path. The generated resource information
 * will only contain a reference to the stream of the found resource. If the
 * path points nowhere, an {@link ResourceResolutionException} is thrown to
 * indicate that the resource couldn't be found.
 * 
 * @author Aurélien Baudet
 * @see ByteResource
 */
public class ClassPathResolver implements ResourceResolver {
	private static final Logger LOG = LoggerFactory.getLogger(ClassPathResolver.class);

	@Override
	public Resource getResource(String path) throws ResourceResolutionException {
		try {
			LOG.debug("Loading resource {} from classpath...", path);
			InputStream stream = getClass().getClassLoader().getResourceAsStream(path.startsWith("/") ? path.substring(1) : path);
			if (stream == null) {
				throw new ResourceResolutionException("Resource " + path + " not found in the classpath", path);
			}
			LOG.debug("Resource {} available in the classpath...", path);
			return new ByteResource(extractName(path), stream);
		} catch (IOException e) {
			throw new ResourceResolutionException("The resource "+path+" is not readable", path, e);
		}
	}

	private static String extractName(String path) {
		String name;
		int lastSlashIdx = path.lastIndexOf('/');
		if(lastSlashIdx>=0) {
			name = path.substring(lastSlashIdx+1);
		} else {
			name = path;
		}
		return name;
	}
}
