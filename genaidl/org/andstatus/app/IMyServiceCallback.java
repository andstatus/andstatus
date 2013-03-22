/*
 * This file is auto-generated.  DO NOT MODIFY.
 * Original file: W:\\Android\\AndStatus\\Repository\\src\\org\\andstatus\\app\\IMyServiceCallback.aidl
 */
package org.andstatus.app;
/**
 * Callback interface used by IMyServiceCallback to send
 * synchronous notifications back to its clients.
 */
public interface IMyServiceCallback extends android.os.IInterface
{
/** Local-side IPC implementation stub class. */
public static abstract class Stub extends android.os.Binder implements org.andstatus.app.IMyServiceCallback
{
private static final java.lang.String DESCRIPTOR = "org.andstatus.app.IMyServiceCallback";
/** Construct the stub at attach it to the interface. */
public Stub()
{
this.attachInterface(this, DESCRIPTOR);
}
/**
 * Cast an IBinder object into an org.andstatus.app.IMyServiceCallback interface,
 * generating a proxy if needed.
 */
public static org.andstatus.app.IMyServiceCallback asInterface(android.os.IBinder obj)
{
if ((obj==null)) {
return null;
}
android.os.IInterface iin = obj.queryLocalInterface(DESCRIPTOR);
if (((iin!=null)&&(iin instanceof org.andstatus.app.IMyServiceCallback))) {
return ((org.andstatus.app.IMyServiceCallback)iin);
}
return new org.andstatus.app.IMyServiceCallback.Stub.Proxy(obj);
}
@Override public android.os.IBinder asBinder()
{
return this;
}
@Override public boolean onTransact(int code, android.os.Parcel data, android.os.Parcel reply, int flags) throws android.os.RemoteException
{
switch (code)
{
case INTERFACE_TRANSACTION:
{
reply.writeString(DESCRIPTOR);
return true;
}
case TRANSACTION_tweetsChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.tweetsChanged(_arg0);
return true;
}
case TRANSACTION_messagesChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.messagesChanged(_arg0);
return true;
}
case TRANSACTION_repliesChanged:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.repliesChanged(_arg0);
return true;
}
case TRANSACTION_dataLoading:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
this.dataLoading(_arg0);
return true;
}
case TRANSACTION_rateLimitStatus:
{
data.enforceInterface(DESCRIPTOR);
int _arg0;
_arg0 = data.readInt();
int _arg1;
_arg1 = data.readInt();
this.rateLimitStatus(_arg0, _arg1);
return true;
}
}
return super.onTransact(code, data, reply, flags);
}
private static class Proxy implements org.andstatus.app.IMyServiceCallback
{
private android.os.IBinder mRemote;
Proxy(android.os.IBinder remote)
{
mRemote = remote;
}
@Override public android.os.IBinder asBinder()
{
return mRemote;
}
public java.lang.String getInterfaceDescriptor()
{
return DESCRIPTOR;
}
/**
     * Called when the service has found new tweets.
     */
@Override public void tweetsChanged(int value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(value);
mRemote.transact(Stub.TRANSACTION_tweetsChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
/**
     * Called when the service has found new messages.
     */
@Override public void messagesChanged(int value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(value);
mRemote.transact(Stub.TRANSACTION_messagesChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
/**
     * Called when the service has found replies.
     */
@Override public void repliesChanged(int value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(value);
mRemote.transact(Stub.TRANSACTION_repliesChanged, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
/**
	 * Called when the service has completed loading data
	 * @param value doesn't have any meaning yet
	 */
@Override public void dataLoading(int value) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(value);
mRemote.transact(Stub.TRANSACTION_dataLoading, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
/**
	 * Called when the service got rateLimitStatus.
	 */
@Override public void rateLimitStatus(int remaining_hits, int hourly_limit) throws android.os.RemoteException
{
android.os.Parcel _data = android.os.Parcel.obtain();
try {
_data.writeInterfaceToken(DESCRIPTOR);
_data.writeInt(remaining_hits);
_data.writeInt(hourly_limit);
mRemote.transact(Stub.TRANSACTION_rateLimitStatus, _data, null, android.os.IBinder.FLAG_ONEWAY);
}
finally {
_data.recycle();
}
}
}
static final int TRANSACTION_tweetsChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 0);
static final int TRANSACTION_messagesChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 1);
static final int TRANSACTION_repliesChanged = (android.os.IBinder.FIRST_CALL_TRANSACTION + 2);
static final int TRANSACTION_dataLoading = (android.os.IBinder.FIRST_CALL_TRANSACTION + 3);
static final int TRANSACTION_rateLimitStatus = (android.os.IBinder.FIRST_CALL_TRANSACTION + 4);
}
/**
     * Called when the service has found new tweets.
     */
public void tweetsChanged(int value) throws android.os.RemoteException;
/**
     * Called when the service has found new messages.
     */
public void messagesChanged(int value) throws android.os.RemoteException;
/**
     * Called when the service has found replies.
     */
public void repliesChanged(int value) throws android.os.RemoteException;
/**
	 * Called when the service has completed loading data
	 * @param value doesn't have any meaning yet
	 */
public void dataLoading(int value) throws android.os.RemoteException;
/**
	 * Called when the service got rateLimitStatus.
	 */
public void rateLimitStatus(int remaining_hits, int hourly_limit) throws android.os.RemoteException;
}
