package fr.sii.ogham.template.thymeleaf.adapter;

import org.thymeleaf.templateresolver.ITemplateResolver;

import fr.sii.ogham.core.resource.resolver.RelativeResolver;
import fr.sii.ogham.core.resource.resolver.ResourceResolver;

/**
 * Adapter that converts general
 * {@link fr.sii.ogham.core.resource.resolver.StringResourceResolver} into
 * Thymeleaf specific {@link StringTemplateResolver}.
 * 
 * @author Aurélien Baudet
 *
 */
public class StringResolverAdapter implements ThymeleafResolverAdapter {

	@Override
	public boolean supports(ResourceResolver resolver) {
		return resolver instanceof fr.sii.ogham.core.resource.resolver.StringResourceResolver
				|| (resolver instanceof RelativeResolver && ((RelativeResolver) resolver).getDelegate() instanceof fr.sii.ogham.core.resource.resolver.StringResourceResolver);
	}

	@Override
	public ITemplateResolver adapt(ResourceResolver resolver) {
		// TODO: manage all other options
		return new StringTemplateResolver();
	}

}
