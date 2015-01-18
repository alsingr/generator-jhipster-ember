package <%=packageName%>.config;

import com.fasterxml.jackson.databind.JsonDeserializer;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;<% if (storage == 'mongo') { %>
import com.fasterxml.jackson.databind.module.SimpleModule;
import <%=packageName%>.domain.util.ObjectIdSerializer;<% } %>
import <%=packageName%>.security.OAuth2ExceptionMixin;
import <%=packageName%>.domain.User;
import <%=packageName%>.domain.util.UserDeserializer;
import <%=packageName%>.web.filter.CachingHttpHeadersFilter;
import com.mycompany.myapp.web.filter.CustomUrlRewriteFilter;
import lombok.extern.slf4j.Slf4j;<% if (storage == 'mongo') { %>
import org.bson.types.ObjectId;<% } %>
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.web.HttpMessageConverters;
import org.springframework.boot.context.embedded.EmbeddedServletContainerFactory;
import org.springframework.boot.context.embedded.ServletContextInitializer;
import org.springframework.boot.context.embedded.tomcat.TomcatEmbeddedServletContainerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.http.converter.json.Jackson2ObjectMapperFactoryBean;
import org.springframework.http.converter.json.MappingJackson2HttpMessageConverter;
import org.springframework.security.oauth2.common.exceptions.OAuth2Exception;
import org.tuckey.web.filters.urlrewrite.gzip.GzipFilter;

import javax.servlet.*;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.Map;

/**
 * Configuration of web application with Servlet 3.0 APIs.
 */
@Slf4j
@Configuration
public class WebConfigurer implements ServletContextInitializer {

    @Autowired
    private Environment env;

    @Autowired
    private UserDeserializer userDeserializer;

    @Value("<%= _.unescape('\$\{server.port:9990}')%>")
    private int port;

    @Bean
    public HttpMessageConverters httpMessageConverters() {
        return new HttpMessageConverters(mappingJackson2HttpMessageConverter());
    }

    @Bean
    public MappingJackson2HttpMessageConverter mappingJackson2HttpMessageConverter() {
        MappingJackson2HttpMessageConverter mappingJackson2JsonView = new MappingJackson2HttpMessageConverter();
        mappingJackson2JsonView.setObjectMapper(objectMapper());
        return mappingJackson2JsonView;
    }

    @Bean
    public ObjectMapper objectMapper() {
        Map<Class<?>, JsonDeserializer<?>> deserializerMap = new HashMap<>();
        deserializerMap.put(User.class, userDeserializer);

        Jackson2ObjectMapperFactoryBean objectMapperFactoryBean = new Jackson2ObjectMapperFactoryBean();
        objectMapperFactoryBean.setIndentOutput(true);
        objectMapperFactoryBean.setDeserializersByType(deserializerMap);
        objectMapperFactoryBean.afterPropertiesSet();

        ObjectMapper objectMapper = objectMapperFactoryBean.getObject();
        objectMapper.configure(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS, false);
        objectMapper.findAndRegisterModules();<% if (storage == 'mongo') { %>

        SimpleModule module = new SimpleModule("ObjectIdModule");
        module.addSerializer(ObjectId.class, new ObjectIdSerializer());
        objectMapper.registerModule(module);
<% } %>
        objectMapper.addMixInAnnotations(OAuth2Exception.class, OAuth2ExceptionMixin.class);

        return objectMapper;
    }

    @Bean
    public EmbeddedServletContainerFactory servletContainer() {
        TomcatEmbeddedServletContainerFactory factory = new TomcatEmbeddedServletContainerFactory(this.port);
        factory.addConnectorCustomizers(connector -> {
            connector.setProperty("bindOnInit", "true");
        });
        return factory;
    }

    @Override
    public void onStartup(ServletContext servletContext) throws ServletException {
        log.info("Web application configuration, using profiles: {}", Arrays.toString(env.getActiveProfiles()));
        EnumSet<DispatcherType> disps = EnumSet.of(DispatcherType.REQUEST, DispatcherType.FORWARD, DispatcherType.ASYNC);

        initUrlRewriteProductionFilter(servletContext, disps);
        initCachingHttpHeadersFilter(servletContext, disps);
        initGzipFilter(servletContext, disps);

        log.info("Web application fully configured");
    }

    /**
     * Initializes the GZip filter.
     */
    private void initGzipFilter(ServletContext servletContext, EnumSet<DispatcherType> disps) {
        log.debug("Registering GZip Filter");

        FilterRegistration.Dynamic compressingFilter = servletContext.addFilter("gzipFilter", new GzipFilter());
        Map<String, String> parameters = new HashMap<>();

        compressingFilter.setInitParameters(parameters);

        compressingFilter.addMappingForUrlPatterns(disps, true, "*.css");
        compressingFilter.addMappingForUrlPatterns(disps, true, "*.json");
        compressingFilter.addMappingForUrlPatterns(disps, true, "*.html");
        compressingFilter.addMappingForUrlPatterns(disps, true, "*.js");
        compressingFilter.addMappingForUrlPatterns(disps, true, "/api/v1/*");
        compressingFilter.addMappingForUrlPatterns(disps, true, "/metrics");
        compressingFilter.addMappingForUrlPatterns(disps, true, "/info");

        compressingFilter.setAsyncSupported(true);
    }

    private void initUrlRewriteProductionFilter(ServletContext servletContext, EnumSet<DispatcherType> disps) {
        log.debug("Registering tuckey urlrewritefilter");

        FilterRegistration.Dynamic urlRewriteFilter = servletContext.addFilter("urlRewriteFilter", new CustomUrlRewriteFilter());
        if (env.acceptsProfiles(Constants.SPRING_PROFILE_PRODUCTION)) {
            urlRewriteFilter.setInitParameter("confPath", "urlrewrite-prod.xml");
        } else {
            urlRewriteFilter.setInitParameter("confPath", "urlrewrite.xml");
        }

        urlRewriteFilter.setInitParameter("logLevel", "slf4j");

        urlRewriteFilter.addMappingForUrlPatterns(disps, true, "/*");
    }

    /**
     * Initializes the cachig HTTP Headers Filter.
     */
    private void initCachingHttpHeadersFilter(ServletContext servletContext, EnumSet<DispatcherType> disps) {
        log.debug("Registering Cachig HTTP Headers Filter");
        FilterRegistration.Dynamic cachingHttpHeadersFilter =
                servletContext.addFilter("cachingHttpHeadersFilter",
                        new CachingHttpHeadersFilter());

        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.woff");
        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.png");
        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.gif");
        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.jpg");
        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.css");
        cachingHttpHeadersFilter.addMappingForUrlPatterns(disps, true, "*.js");
        cachingHttpHeadersFilter.setAsyncSupported(true);
    }
}
