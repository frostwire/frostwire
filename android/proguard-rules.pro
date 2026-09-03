# okhttp3
-dontwarn okhttp3.**
-dontwarn okio.**
#D8: Type `org.conscrypt.Conscrypt` was not found
-dontwarn okhttp3.internal.platform.ConscryptPlatform
-dontwarn javax.annotation.**
-keep class androidx.lifecycle.** { *; }
-keep class okhttp3.Headers { *; }
-keep class org.apache.http.client.utils.URLEncodedUtils { *; }
-keep class org.conscrypt.Conscrypt { *; }
-keep class org.conscrypt.org.Conscrypt$ProviderBuilder { *; }
#-keep class androidx.core.view.accessibility.AccessibilityManagerCompat$TouchExplorationStateChangeListenerWrapper { *; }
-keep class android.net.http.AndroidHttpClient { *; }
-keep class androidx.core.app.CoreComponentFactory { *; }
-keep class org.conscrypt.ConscryptHostnameVerifier { *; }
-keep class com.crashlytics.** { *; }
-dontwarn com.crashlytics.**
-keepnames class okhttp3.internal.publicsuffix.PublicSuffixDatabase {
    void <init>();
}


#  VUNGLE
-dontwarn com.vungle.**
-dontnote com.vungle.**
-keep class com.vungle.** { *; }
-keep class javax.inject.* {
    void <init>();
}
-dontwarn de.greenrobot.event.util.**
-dontwarn rx.internal.util.unsafe.**
-keepclassmembers class rx.internal.util.unsafe.*ArrayQueue*Field* {
    long producerIndex;
    long consumerIndex;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueProducerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode producerNode;
}
-keepclassmembers class rx.internal.util.unsafe.BaseLinkedQueueConsumerNodeRef {
    rx.internal.util.atomic.LinkedQueueNode consumerNode;
}
-keep class rx.schedulers.Schedulers { public static <methods>; }
-keep class rx.schedulers.ImmediateScheduler { public <methods>; }
-keep class rx.schedulers.TestScheduler { public <methods>; }
-keep class rx.schedulers.Schedulers { public static ** test(); }
# EOVUNGLE

# MoPub
-keep class com.mopub.mobileads.WebViewCacheService { *; }
-dontwarn com.mopub.common.MoPubReward
-dontwarn com.mopub.nativeads.*
-dontwarn com.mopub.mobileads.MoPubRewardedVideoManager
-dontwarn com.mopub.mobileads.CustomEventRewardedVideo

-dontwarn javax.annotation.Nullable
-dontwarn javax.annotation.ParametersAreNonnullByDefault
-dontwarn org.conscrypt.Conscrypt
-dontwarn org.conscrypt.OpenSSLProvider

# GoogleBillingClient
-keep class com.android.billingclient.api.BillingClientImpl$* { *; }

# jlibtorrent
-keep class com.frostwire.jlibtorrent.swig.libtorrent_jni {*;}

-keep class libcore.io.Memory { *; }

# chaquopy
-keep class com.chaquo.python.** { *; }
-dontwarn com.chaquo.python.**

# coil3 - PlatformContext is not needed at runtime
-dontwarn coil3.PlatformContext

# GMS
-keep public class com.google.android.gms.** { public protected *; }

# keep all constructors
-keep class * {
    <init>(...);
}

-keep public class * implements androidx.versionedparcelable.VersionedParcelable {
  <init>();
}

# to keep all the names and avoid code mangling
-keepnames class ** {*;}
-keepclassmembers enum * {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}
-dontobfuscate
#-optimizations !code/simplification/arithmetic,!code/simplification/cast,!field/*,!class/merging/*,!code/allocation/variable

# Netty (rUDP transport) references optional codecs it never loads on Android
# (brotli/zstd/lz4/lzma, UDT, SCTP, protobuf-nano, tcnative, log facades).
# Rules below are the -dontwarn set R8 generates for those references.
-dontwarn com.aayushatharva.brotli4j.Brotli4jLoader
-dontwarn com.aayushatharva.brotli4j.decoder.DecoderJNI$Status
-dontwarn com.aayushatharva.brotli4j.decoder.DecoderJNI$Wrapper
-dontwarn com.aayushatharva.brotli4j.encoder.BrotliEncoderChannel
-dontwarn com.aayushatharva.brotli4j.encoder.Encoder$Mode
-dontwarn com.aayushatharva.brotli4j.encoder.Encoder$Parameters
-dontwarn com.barchart.udt.OptionUDT
-dontwarn com.barchart.udt.SocketUDT
-dontwarn com.barchart.udt.StatusUDT
-dontwarn com.barchart.udt.TypeUDT
-dontwarn com.barchart.udt.nio.ChannelUDT
-dontwarn com.barchart.udt.nio.KindUDT
-dontwarn com.barchart.udt.nio.NioServerSocketUDT
-dontwarn com.barchart.udt.nio.NioSocketUDT
-dontwarn com.barchart.udt.nio.RendezvousChannelUDT
-dontwarn com.barchart.udt.nio.SelectorProviderUDT
-dontwarn com.barchart.udt.nio.ServerSocketChannelUDT
-dontwarn com.barchart.udt.nio.SocketChannelUDT
-dontwarn com.fasterxml.aalto.AsyncByteArrayFeeder
-dontwarn com.fasterxml.aalto.AsyncInputFeeder
-dontwarn com.fasterxml.aalto.AsyncXMLInputFactory
-dontwarn com.fasterxml.aalto.AsyncXMLStreamReader
-dontwarn com.fasterxml.aalto.stax.InputFactoryImpl
-dontwarn com.github.luben.zstd.Zstd
-dontwarn com.github.luben.zstd.ZstdInputStreamNoFinalizer
-dontwarn com.github.luben.zstd.util.Native
-dontwarn com.google.protobuf.ExtensionRegistry
-dontwarn com.google.protobuf.ExtensionRegistryLite
-dontwarn com.google.protobuf.MessageLite$Builder
-dontwarn com.google.protobuf.MessageLite
-dontwarn com.google.protobuf.MessageLiteOrBuilder
-dontwarn com.google.protobuf.Parser
-dontwarn com.google.protobuf.nano.CodedOutputByteBufferNano
-dontwarn com.google.protobuf.nano.MessageNano
-dontwarn com.jcraft.jzlib.Deflater
-dontwarn com.jcraft.jzlib.Inflater
-dontwarn com.jcraft.jzlib.JZlib$WrapperType
-dontwarn com.jcraft.jzlib.JZlib
-dontwarn com.ning.compress.BufferRecycler
-dontwarn com.ning.compress.lzf.ChunkDecoder
-dontwarn com.ning.compress.lzf.ChunkEncoder
-dontwarn com.ning.compress.lzf.LZFChunk
-dontwarn com.ning.compress.lzf.LZFEncoder
-dontwarn com.ning.compress.lzf.util.ChunkDecoderFactory
-dontwarn com.ning.compress.lzf.util.ChunkEncoderFactory
-dontwarn com.oracle.svm.core.annotate.TargetClass
-dontwarn com.sun.nio.sctp.AbstractNotificationHandler
-dontwarn com.sun.nio.sctp.Association
-dontwarn com.sun.nio.sctp.MessageInfo
-dontwarn com.sun.nio.sctp.NotificationHandler
-dontwarn com.sun.nio.sctp.SctpChannel
-dontwarn com.sun.nio.sctp.SctpServerChannel
-dontwarn com.sun.nio.sctp.SctpSocketOption
-dontwarn com.sun.nio.sctp.SctpStandardSocketOptions$InitMaxStreams
-dontwarn com.sun.nio.sctp.SctpStandardSocketOptions
-dontwarn gnu.io.CommPort
-dontwarn gnu.io.CommPortIdentifier
-dontwarn gnu.io.SerialPort
-dontwarn io.netty.internal.tcnative.AsyncSSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.AsyncTask
-dontwarn io.netty.internal.tcnative.Buffer
-dontwarn io.netty.internal.tcnative.CertificateCallback
-dontwarn io.netty.internal.tcnative.CertificateCompressionAlgo
-dontwarn io.netty.internal.tcnative.CertificateVerifier
-dontwarn io.netty.internal.tcnative.Library
-dontwarn io.netty.internal.tcnative.ResultCallback
-dontwarn io.netty.internal.tcnative.SSL
-dontwarn io.netty.internal.tcnative.SSLContext
-dontwarn io.netty.internal.tcnative.SSLPrivateKeyMethod
-dontwarn io.netty.internal.tcnative.SSLSession
-dontwarn io.netty.internal.tcnative.SSLSessionCache
-dontwarn io.netty.internal.tcnative.SessionTicketKey
-dontwarn io.netty.internal.tcnative.SniHostNameMatcher
-dontwarn io.netty.pkitesting.CertificateBuilder$Algorithm
-dontwarn io.netty.pkitesting.CertificateBuilder
-dontwarn io.netty.pkitesting.X509Bundle
-dontwarn javax.naming.NamingException
-dontwarn javax.naming.directory.DirContext
-dontwarn javax.naming.directory.InitialDirContext
-dontwarn javax.xml.stream.XMLStreamException
-dontwarn lzma.sdk.ICodeProgress
-dontwarn lzma.sdk.lzma.Encoder
-dontwarn net.jpountz.lz4.LZ4Compressor
-dontwarn net.jpountz.lz4.LZ4Exception
-dontwarn net.jpountz.lz4.LZ4Factory
-dontwarn net.jpountz.lz4.LZ4FastDecompressor
-dontwarn net.jpountz.xxhash.XXHash32
-dontwarn net.jpountz.xxhash.XXHashFactory
-dontwarn org.apache.log4j.Level
-dontwarn org.apache.log4j.Logger
-dontwarn org.apache.log4j.Priority
-dontwarn org.apache.logging.log4j.Level
-dontwarn org.apache.logging.log4j.LogManager
-dontwarn org.apache.logging.log4j.Logger
-dontwarn org.apache.logging.log4j.message.MessageFactory
-dontwarn org.apache.logging.log4j.spi.ExtendedLogger
-dontwarn org.apache.logging.log4j.spi.ExtendedLoggerWrapper
-dontwarn org.jboss.marshalling.ByteInput
-dontwarn org.jboss.marshalling.ByteOutput
-dontwarn org.jboss.marshalling.Marshaller
-dontwarn org.jboss.marshalling.MarshallerFactory
-dontwarn org.jboss.marshalling.MarshallingConfiguration
-dontwarn org.jboss.marshalling.Unmarshaller
-dontwarn org.osgi.annotation.bundle.Export
-dontwarn org.slf4j.ILoggerFactory
-dontwarn org.slf4j.Logger
-dontwarn org.slf4j.LoggerFactory
-dontwarn org.slf4j.Marker
-dontwarn org.slf4j.helpers.FormattingTuple
-dontwarn org.slf4j.helpers.MessageFormatter
-dontwarn org.slf4j.helpers.NOPLoggerFactory
-dontwarn org.slf4j.spi.LocationAwareLogger
-dontwarn reactor.blockhound.integration.BlockHoundIntegration
