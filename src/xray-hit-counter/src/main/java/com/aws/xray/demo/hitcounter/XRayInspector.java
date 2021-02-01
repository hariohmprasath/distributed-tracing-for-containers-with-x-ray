package com.aws.xray.demo.hitcounter;

import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import javax.servlet.http.HttpServletRequest;

import com.amazonaws.xray.AWSXRay;
import com.amazonaws.xray.entities.Segment;
import com.amazonaws.xray.spring.aop.AbstractXRayInterceptor;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;

import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

@Aspect
@Component
public class XRayInspector extends AbstractXRayInterceptor {

    private static final String SESSION_ID = "SessionId";
    private static final String ARGS = "Args";

    @Override
    @Pointcut("@within(com.amazonaws.xray.spring.aop.XRayEnabled)")
    public void xrayEnabledClasses() {
    }

    @Override
    public Object traceAroundMethods(ProceedingJoinPoint pjp) throws Throwable {
        Segment segment = AWSXRay.getCurrentSegment();
        ServletRequestAttributes attributes = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attributes != null) {
            HttpServletRequest request = attributes.getRequest();
            Map<String, Object> annotationMap = new HashMap<>();
            Optional<String> args = Arrays.stream(pjp.getArgs()).map(Object::toString).reduce((a, b) -> a + ", " + b);
            args.ifPresent(s -> annotationMap.put(ARGS, s));
            annotationMap.put(SESSION_ID, request.getSession().getId());
            segment.setAnnotations(annotationMap);
        }

        return super.traceAroundMethods(pjp);
    }

    @Override
    public Object traceAroundRepositoryMethods(ProceedingJoinPoint pjp) throws Throwable {
        return super.traceAroundRepositoryMethods(pjp);
    }
}
