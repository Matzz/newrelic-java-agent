/*
 *
 *  * Copyright 2020 New Relic Corporation. All rights reserved.
 *  * SPDX-License-Identifier: Apache-2.0
 *
 */
package com.nr.agent.instrumentation.web.reactive;

import com.newrelic.agent.bridge.AgentBridge;
import com.newrelic.agent.bridge.Transaction;
import com.newrelic.agent.bridge.TransactionNamePriority;
import com.newrelic.api.agent.NewRelic;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.lang.reflect.Method;
import java.util.logging.Level;

public class SpringControllerUtility {
    private static final String CGLIB_CLASS_SUFFIX = "$$EnhancerBy";


    /**
     * Return the top level path String on the target controller class, determined by a @RequestMapping annotation.
     * This includes any @RequestMapping annotations present on an implemented interface or extended controller class.
     *
     * @param controllerClass the controller class to search for a @RequestMapping annotation
     * @return the path if available; null otherwise
     */
    public static String retrieveRootMappingPathFromController(Class<?> controllerClass) {
        RequestMapping rootPathMapping = AnnotationUtils.findAnnotation(controllerClass, RequestMapping.class);
        return rootPathMapping != null ? SpringControllerUtility.getPathValue(rootPathMapping.value(), rootPathMapping.path()) : null;
    }

    /**
     * Return the mapping path for the target method by looking for the XXXXMapping annotation on the method based on the
     * supplied httpMethod value. These include annotations present on implemented interface methods or methods
     * implemented/overridden from extended classes.
     *
     * @param method the method to search
     * @param httpMethod the HTTP method (verb) being invoked (GET, POST, etc)
     * @return the path if available; null otherwise
     */
    public static String retrieveMappingPathFromHandlerMethod(Method method, String httpMethod) {
        //Check for a generic RequestMapping annotation. If nothing is found, do a targeted search for the annotation
        //based on the httpMethod value.
        RequestMapping requestMapping = AnnotationUtils.findAnnotation(method, RequestMapping.class);
        if (requestMapping != null) {
            String pathValue = getPathValue(requestMapping.value(), requestMapping.path());
            if (pathValue != null) {
                return pathValue;
            }
        }

        switch (httpMethod) {
            case "PUT":
                PutMapping putMapping = AnnotationUtils.findAnnotation(method, PutMapping.class);
                if (putMapping != null) {
                    return getPathValue(putMapping.value(), putMapping.path());
                }
                break;
            case "DELETE":
                DeleteMapping deleteMapping = AnnotationUtils.findAnnotation(method, DeleteMapping.class);
                if (deleteMapping != null) {
                    return getPathValue(deleteMapping.value(), deleteMapping.path());
                }
                break;
            case "POST":
                PostMapping postMapping = AnnotationUtils.findAnnotation(method, PostMapping.class);
                if (postMapping != null) {
                    return getPathValue(postMapping.value(), postMapping.path());
                }
                break;
            case "PATCH":
                PatchMapping patchMapping = AnnotationUtils.findAnnotation(method, PatchMapping.class);
                if (patchMapping != null) {
                    return getPathValue(patchMapping.value(), patchMapping.path());
                }
                break;
            case "GET":
                GetMapping getMapping = AnnotationUtils.findAnnotation(method, GetMapping.class);
                if (getMapping != null) {
                    return getPathValue(getMapping.value(), getMapping.path());
                }
                break;
        }

        return null;
    }

    /**
     * Generate and set a transaction name from a controller's top level and method level mappings.
     *
     * @param transaction the transaction to set the name for
     * @param httpMethod theHTTP method being executed
     * @param rootPath the top level controller mapping path
     * @param methodPath the method mapping path
     */
    public static void assignTransactionNameFromControllerAndMethodRoutes(Transaction transaction, String httpMethod,
            String rootPath, String methodPath) {
        httpMethod = httpMethod == null ? "GET" : httpMethod;

        String txnName = getRouteName(rootPath, methodPath, httpMethod);
        if (NewRelic.getAgent().getLogger().isLoggable(Level.FINEST)) {
            NewRelic.getAgent()
                    .getLogger()
                    .log(Level.FINEST, "SpringControllerUtility::assignTransactionNameFromControllerAndMethodRoutes (6.0.0): calling transaction.setTransactionName to [{0}] " +
                            "with FRAMEWORK_HIGH and override false, txn {1}, ", txnName, AgentBridge.getAgent().getTransaction().toString());
        }

        transaction.setTransactionName(TransactionNamePriority.FRAMEWORK_HIGH, false, "SpringController",
                txnName);

    }

    /**
     * Generate and set a transaction name from a controller class name and method
     *
     * @param transaction the transaction to set the name for
     * @param controllerClass the target controller class
     * @param method the method being invoked on the controller
     */
    public static void assignTransactionNameFromControllerAndMethod(Transaction transaction, Class<?> controllerClass, Method method) {
        String txnName = '/' + getClassAndControllerString(controllerClass, method, false);

        if (NewRelic.getAgent().getLogger().isLoggable(Level.FINEST)) {
            NewRelic.getAgent()
                    .getLogger()
                    .log(Level.FINEST, "SpringControllerUtility::assignTransactionNameFromControllerAndMethod (6.0.0): " +
                            "calling transaction.setTransactionName to [{0}] " +
                            "with FRAMEWORK_HIGH and override false, txn {1}, ", txnName, transaction.toString());
        }

        transaction.setTransactionName(TransactionNamePriority.FRAMEWORK_HIGH, false, "SpringController", txnName);
    }

    /**
     * Generate a route name from the given root path, method path and HTTP method. The resulting route name will be:
     * /root-mapping/method-mapping (METHOD). For example: api/v1/customer/fetch/{id} (GET)
     *
     * @param rootPath
     * @param methodPath
     * @param httpMethod
     * @return
     */
    private static String getRouteName(String rootPath, String methodPath, String httpMethod) {
        StringBuilder fullPath = new StringBuilder();
        if (rootPath != null && !rootPath.isEmpty()) {
            if (rootPath.endsWith("/")) {
                fullPath.append(rootPath, 0, rootPath.length() - 1);
            } else {
                fullPath.append(rootPath);
            }
        }

        if (methodPath != null && !methodPath.isEmpty()) {
            if (!methodPath.startsWith("/")) {
                fullPath.append('/');
            }
            if (methodPath.endsWith("/")) {
                fullPath.append(methodPath, 0, methodPath.length() - 1);
            } else {
                fullPath.append(methodPath);
            }
        }

        if (httpMethod != null) {
            fullPath.append(" (").append(httpMethod).append(')');
        }

        return fullPath.toString();
    }

    /**
     * Get a path value from one of the mapping annotation's attributes: value or path. If the arrays
     * contain more than one String, the first element is used.
     *
     * @param values the values array from the annotation attribute
     * @param path the path array from the annotation attribute
     *
     * @return a mapping path, from the values or paths arrays
     */
    private static String getPathValue(String[] values, String[] path) {
        String result = null;
        if (values != null) {
            if (values.length > 0 && !values[0].contains("error.path")) {
                result = values[0];
            }
            if (result == null && path != null) {
                if (path.length > 0 && !path[0].contains("error.path")) {
                    result = path[0];
                }
            }
        }

        return result;
    }

    public static void setTracedMethodMetricName(Transaction transaction, Class<?> controllerClass, Method method) {
        transaction.getTracedMethod().setMetricName("Java", getClassAndControllerString(controllerClass, method, true));
    }

    private static String getClassAndControllerString(Class<?> controllerClass, Method method, boolean includePackagePrefix) {
        String result;
        if (controllerClass != null && method != null) {
            String controllerName = includePackagePrefix ? controllerClass.getName() : controllerClass.getSimpleName();
            int indexOf = controllerName.indexOf(CGLIB_CLASS_SUFFIX);
            if (indexOf > 0) {
                controllerName = controllerName.substring(0, indexOf);
            }
            result = controllerName + '/' + method.getName();
        } else {
            result = "Unknown";
        }

        return result;
    }
}