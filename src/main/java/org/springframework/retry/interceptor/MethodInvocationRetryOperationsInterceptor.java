package org.springframework.retry.interceptor;


import java.util.Arrays;
import org.aopalliance.intercept.MethodInterceptor;
import org.aopalliance.intercept.MethodInvocation;
import org.springframework.aop.ProxyMethodInvocation;
import org.springframework.retry.RecoveryCallback;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.RetryOperations;
import org.springframework.retry.support.RetryTemplate;
import org.springframework.util.Assert;
import org.springframework.util.StringUtils;

/**
 * This is an enhancement of the already existing {@link RetryOperationsInterceptor} which adds
 * {@link MethodInvocation} information in the {@link RetryContext} for using it down the line (e.g.
 * : for monitoring purposes).
 * <p>
 * In order to keep things simple (for maintenance in case new version of spring-retry are being
 * introduced), all the code of the {@link RetryOperationsInterceptor} has been copied into this
 * class and the additions are clearly marked with comments.
 *
 * <pre>
 * context.setAttribute(METHOD_INVOCATION, invocation);
 * </pre>
 *
 * @see RetryOperationsInterceptor
 */
public class MethodInvocationRetryOperationsInterceptor implements MethodInterceptor {

  /**
   * The method being invoked.
   */
  public static final String METHOD_INVOCATION = "context.method.invocation";

  private RetryOperations retryOperations = new RetryTemplate();

  private MethodInvocationRecoverer<?> recoverer;

  private String label;

  public void setLabel(String label) {
    this.label = label;
  }

  public void setRetryOperations(RetryOperations retryTemplate) {
    Assert.notNull(retryTemplate, "'retryOperations' cannot be null.");
    this.retryOperations = retryTemplate;
  }

  public void setRecoverer(MethodInvocationRecoverer<?> recoverer) {
    this.recoverer = recoverer;
  }

  public Object invoke(final MethodInvocation invocation) throws Throwable {

    String name;
    if (StringUtils.hasText(label)) {
      name = label;
    } else {
      name = invocation.getMethod().toGenericString();
    }
    final String label = name;

    RetryCallback<Object, Throwable> retryCallback = new RetryCallback<Object, Throwable>() {

      public Object doWithRetry(RetryContext context) throws Exception {

        context.setAttribute(RetryContext.NAME, label);

        /**
         *  BEGINNING OF CONTEXT ENHANCEMENT
         */
        context.setAttribute(METHOD_INVOCATION, invocation);
        /**
         *  END OF CONTEXT ENHANCEMENT
         */

        /*
         * If we don't copy the invocation carefully it won't keep a reference to
         * the other interceptors in the chain. We don't have a choice here but to
         * specialise to ReflectiveMethodInvocation (but how often would another
         * implementation come along?).
         */
        if (invocation instanceof ProxyMethodInvocation) {
          try {
            return ((ProxyMethodInvocation) invocation).invocableClone().proceed();
          } catch (Exception e) {
            throw e;
          } catch (Error e) {
            throw e;
          } catch (Throwable e) {
            throw new IllegalStateException(e);
          }
        } else {
          throw new IllegalStateException(
              "MethodInvocation of the wrong type detected - this should not happen with Spring AOP, "
                  +
                  "so please raise an issue if you see this exception");
        }
      }

    };

    if (recoverer != null) {
      ItemRecovererCallback recoveryCallback = new ItemRecovererCallback(
          invocation.getArguments(), recoverer);
      return this.retryOperations.execute(retryCallback, recoveryCallback);
    }

    return this.retryOperations.execute(retryCallback);

  }


  /**
   * @author Dave Syer
   */
  private static final class ItemRecovererCallback implements RecoveryCallback<Object> {

    private final Object[] args;

    private final MethodInvocationRecoverer<?> recoverer;

    /**
     * @param args the item that failed.
     */
    private ItemRecovererCallback(Object[] args, MethodInvocationRecoverer<?> recoverer) {
      this.args = Arrays.asList(args).toArray();
      this.recoverer = recoverer;
    }

    public Object recover(RetryContext context) {
      return recoverer.recover(args, context.getLastThrowable());
    }

  }

}
