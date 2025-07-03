package io.github.hiant.common.utils;

import lombok.NonNull;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanWrapper;
import org.springframework.beans.BeanWrapperImpl;
import org.springframework.beans.factory.config.YamlPropertiesFactoryBean;
import org.springframework.beans.factory.support.BeanDefinitionBuilder;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.BindException;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.boot.context.properties.source.MapConfigurationPropertySource;
import org.springframework.core.io.FileSystemResource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Properties;

/**
 * Utility class for dynamically registering and updating Spring beans from external configuration files.
 * It supports loading properties from .properties and .yml/.yaml files.
 *
 * @author liudong.work@gmail.com
 * Created at: 2025/7/3 10:26
 */
@Slf4j
public class FileToBeanUtils {

    private static FileWatcher fileWatcher;

    private static final FileWatcherProperties DEFAULT = new FileWatcherProperties();

    /**
     * Initializes the FileWatcher with the given properties.
     * If a FileWatcher already exists, it will be closed and a new one will be created.
     *
     * @param properties The properties for configuring the FileWatcher.
     * @throws IOException If an I/O error occurs.
     */
    public synchronized static void init(FileWatcherProperties properties) throws IOException {
        if (fileWatcher != null) {
            fileWatcher.close();
        }
        fileWatcher = new FileWatcher(properties);
    }

    /**
     * Registers a dynamic Spring bean that is configured from an external file.
     * The bean's properties will be loaded from the specified file (properties or YAML format).
     * Any changes to the file will trigger an update of the registered bean.
     *
     * @param filePath  The absolute path to the configuration file.
     * @param beanName  The name of the bean to register.
     * @param beanClass The class of the bean to register.
     * @throws IOException If an I/O error occurs during file operations.
     */
    public static void registerBean(@NonNull Path filePath, String beanName, Class<?> beanClass) throws IOException {
        if (fileWatcher == null) {
            init(DEFAULT);
        }
        @NonNull Path finalFilePath = filePath.toRealPath();
        fileWatcher.registerFileListener(finalFilePath, new FileChangeListener() {
            @Override
            public Path getFilePath() {
                return finalFilePath;
            }

            @Override
            public void onFileChange(Path filePath) {
                BeanDefinitionBuilder builder = BeanDefinitionBuilder.genericBeanDefinition(beanClass);
                try {
                    if (Files.exists(filePath)) {
                        String fileName = filePath.getFileName().toString();
                        log.info("Attempting to load properties from file: {}", filePath);
                        if (fileName.endsWith(".properties")) {
                            Properties properties = new Properties();
                            try (InputStream is = Files.newInputStream(filePath)) {
                                properties.load(is);
                            }
                            log.info("Loaded properties: {}", properties);
                            properties.forEach((key, value) -> builder.addPropertyValue(key.toString(), value));
                        } else if (fileName.endsWith(".yml") || fileName.endsWith(".yaml")) {
                            YamlPropertiesFactoryBean yaml = new YamlPropertiesFactoryBean();
                            yaml.setResources(new FileSystemResource(filePath.toFile()));
                            Properties properties = yaml.getObject();
                            if (properties != null) {
                                log.info("Loaded YAML properties: {}", properties);
                                // Check for @ConfigurationProperties and bind if present
                                if (beanClass.isAnnotationPresent(ConfigurationProperties.class)) {
                                    ConfigurationProperties annotation = beanClass.getAnnotation(ConfigurationProperties.class);
                                    String prefix = annotation.prefix();

                                    Binder binder = new Binder(new MapConfigurationPropertySource(properties));

                                    try {
                                        Object boundBean = beanClass.newInstance();
                                        binder.bind(prefix, Bindable.ofInstance(boundBean));

                                        // Apply bound properties to BeanDefinitionBuilder
                                        BeanWrapper beanWrapper = new BeanWrapperImpl(boundBean);
                                        for (java.beans.PropertyDescriptor pd : beanWrapper.getPropertyDescriptors()) {
                                            if (beanWrapper.isReadableProperty(pd.getName()) && beanWrapper.isWritableProperty(pd.getName())) {
                                                Object value = beanWrapper.getPropertyValue(pd.getName());
                                                if (value != null) {
                                                    builder.addPropertyValue(pd.getName(), value);
                                                }
                                            }
                                        }
                                        log.info("Bound properties to bean {} with prefix '{}'".concat(prefix.isEmpty() ? "(no prefix)" : prefix), beanName);
                                    } catch (InstantiationException | IllegalAccessException | BindException ex) {
                                        log.error("Failed to bind properties to bean {}: {}", beanName, ex.getMessage());
                                        // Fallback to direct property setting if binding fails
                                        properties.forEach((key, value) -> builder.addPropertyValue(key.toString(), value));
                                    }
                                } else {
                                    // No @ConfigurationProperties, apply directly
                                    properties.forEach((key, value) -> builder.addPropertyValue(key.toString(), value));
                                }
                            }
                        }
                    } else {
                        log.warn("File does not exist: {}", filePath);
                    }
                } catch (org.yaml.snakeyaml.error.YAMLException e) {
                    log.error("Failed to parse YAML from file: {}", filePath, e);
                } catch (IllegalArgumentException e) {
                    log.error("Failed to parse properties from file: {}", filePath, e);
                } catch (IOException e) {
                    log.error("Failed to read file: {}", filePath, e);
                }

                SpringContextUtils.updateBeanDefinition(beanName, builder.getBeanDefinition());
                log.info("Update BeanDefinition: {}({})", beanName, beanClass.getName());
            }
        }).onFileChange(filePath);
    }

}
