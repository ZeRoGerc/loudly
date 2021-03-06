package ly.loud.loudly.util;

import android.support.annotation.CheckResult;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;

import java.net.UnknownHostException;

import ly.loud.loudly.base.exceptions.FatalException;
import ly.loud.loudly.base.exceptions.NoNetworkConnectionException;
import rx.Observable;
import rx.Scheduler;
import rx.Single;
import solid.functions.Func0;

import static java.util.concurrent.TimeUnit.SECONDS;

/**
 * Some utility functions for Rx
 */
public class RxUtils {
    /**
     * Change scheduler, on which observable was previously subscribed
     *
     * @param initial      An observable
     * @param newScheduler New scheduler
     * @param <T>          Type of elements in observable
     * @return Observable, subscribed on other scheduler
     */
    @CheckResult
    @NonNull
    public static <T> Observable<T> changeSubscription(@NonNull Observable<T> initial,
                                                       @NonNull Scheduler newScheduler) {
        return Observable
                .create(observer -> initial
                                .map(element -> Single.defer(() -> Single.just(element)))
                                .doOnCompleted(observer::onCompleted)
                                .subscribe(
                                        single -> single
                                                .observeOn(newScheduler)
                                                .subscribe(observer::onNext, observer::onError),
                                        observer::onError
                                )
                );
    }

    /**
     * Change scheduler, on which single was previously subscribed
     *
     * @param initial      A single
     * @param newScheduler New scheduler
     * @param <T>          Type of elements in observable
     * @return Single, subscribed on other scheduler
     */
    @CheckResult
    @NonNull
    public static <T> Single<T> changeSubscription(@NonNull Single<T> initial,
                                                   @NonNull Scheduler newScheduler) {
        return Single
                .create(observer -> initial
                                .map(element -> Single.defer(() -> Single.just(element)))
                                .subscribe(
                                        single -> single
                                                .observeOn(newScheduler)
                                                .subscribe(observer::onSuccess, observer::onError),
                                        observer::onError
                                )
                );
    }

    /**
     * Infinitely resubscribe to this Observable after specified amount of time
     *
     * @param observable       An observable to resubscribe
     * @param intervalSupplier Supplier of interval for updates
     * @param <T>              Type of elements in observable
     * @return Infinitely repeated observable, emitted with pauses between them
     */
    @CheckResult
    @NonNull
    public static <T> Observable<T> repeat(@NonNull Observable<T> observable,
                                           @NonNull Func0<Long> intervalSupplier) {
        return observable
                .repeatWhen(
                        handler -> handler.flatMap(
                                aVoid -> Observable
                                        .just(aVoid)
                                        .delay(intervalSupplier.call(), SECONDS)
                        )
                );
    }

    /**
     * Non null predicate
     *
     * @param object Object to check
     * @param <T>    Type of object
     * @return True, if object is not null, false otherwise
     */
    public static <T> boolean nonNull(@Nullable T object) {
        return object != null;
    }

    /**
     * Retry this observable N times with exponential backoff (1 sec, 2 sec, 4, 8, ...) and
     * throw and exception if retrying didn't help
     *
     * @param initial       Initial observable
     * @param throwIfFailed Exception which will be thrown after fail
     * @param <T>           Type of elements
     * @return Resulting observable
     */
    @CheckResult
    @NonNull
    public static <T> Observable<T> retry3TimesAndFail(@NonNull Observable<T> initial,
                                                       @NonNull Exception throwIfFailed) {
        return initial
                .retryWhen(handler -> handler
                        .flatMap(error -> {
                            // Don't retry if fatal
                            if (error instanceof FatalException) {
                                return Observable.error(error);
                            }
                            if (error instanceof UnknownHostException) {
                                return Observable.error(new NoNetworkConnectionException(error));
                            }
                            return Observable.just(error);
                        })
                        .zipWith(Observable.range(0, 3), (e, i) -> i)
                        .flatMap(time -> Observable.timer(2 ^ time, SECONDS))
                        .concatWith(Observable.error(throwIfFailed)));
    }
}
