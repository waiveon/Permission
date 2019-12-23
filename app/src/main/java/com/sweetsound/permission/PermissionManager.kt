package com.sweetsound.permission

import android.app.Activity
import android.app.AlertDialog
import android.content.DialogInterface
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.PermissionInfo
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

class PermissionManager(): Activity() {
    companion object {
        val REQUEST_PERMISSION_LIST = "REQUEST_PERMISSION_LIST"
        val DENIED_SERIALIZABLE_PERMISSION_ARRAYLIST = "DENIED_SERIALIZABLE_PERMISSION_ARRAYLIST"

        fun check(activity: Activity) {
            check(activity, ArrayList(), -1)
        }

        fun check(activity: Activity, requestCode: Int) {
            check(activity, ArrayList(), requestCode)
        }

        fun check(activity: Activity, requestPermissionList: ArrayList<String>, requestCode: Int) {
            val intent = Intent(activity, PermissionManager::class.java)
            intent.putExtra(REQUEST_PERMISSION_LIST, requestPermissionList)

            activity.startActivityForResult(intent, requestCode)
        }
    }

    private val REQUEST_CODE = 0

    private val mCheckPermissions = ArrayList<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val intent = intent
        // 자바에서 호출 했으면 이걸로 받아올테고
        var requestPermissionList = intent.getStringArrayListExtra(REQUEST_PERMISSION_LIST)

        // Kotlin이나 위 open 함수를 사용 했으면 이걸로 받아 온다.
        if (requestPermissionList == null) {
            requestPermissionList = intent.getSerializableExtra(REQUEST_PERMISSION_LIST) as ArrayList<String>
        }

        if (requestPermissionList == null || requestPermissionList.isEmpty()) {
            // 요청한 것이 없다면 Manifest에 설정되어 있는 permission이 있는지 체크 한다.
            val needPermissionList = getNeedPermissionList()
            needPermissionList.forEach {
                val permissionInfo = packageManager.getPermissionInfo(it, PackageManager.GET_META_DATA)

                // 권한 수락이 필요한 Permission이면 권한을 가지고 있는지 체크
                if (permissionInfo.protectionLevel > PermissionInfo.PROTECTION_NORMAL &&
                        checkPermission(it) == false) {
                    // 권한이 없다면 권한이 없는 애들을 모아다 요청 하자.
                    mCheckPermissions.add(it)
                }
            }
        } else {
            requestPermissionList.forEach {
                // 권한을 가지고 있는지 체크
                if (checkPermission(it) == false) {
                    // 권한이 없다면 권한이 없는 애들을 모아다 요청 하자.
                    mCheckPermissions.add(it)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()

        // 체크해야 할 permission이 있다면 요청 한다.
        if (mCheckPermissions.isEmpty() == false) {
            val checkPermissionsArr = arrayOfNulls<String>(mCheckPermissions.size)
            mCheckPermissions.toArray(checkPermissionsArr)

            ActivityCompat.requestPermissions(this, checkPermissionsArr, REQUEST_CODE)
        } else {
            finish()
        }
    }

    private fun checkPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(this, permission) == PackageManager.PERMISSION_GRANTED
    }

    private fun getNeedPermissionList(): Array<String> {
        var requestedPermissions: Array<String> = emptyArray()

        try {
            val packageInfo = packageManager.getPackageInfo(packageName, PackageManager.GET_PERMISSIONS)

            if (packageInfo != null) {
                requestedPermissions = packageInfo.requestedPermissions
            }
        } catch (e: PackageManager.NameNotFoundException) { }

        return requestedPermissions
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        val deniedPermissions = ArrayList<String>()
        var needShowDialog = false

        mCheckPermissions.clear()

        when (requestCode) {
            REQUEST_CODE -> {
                grantResults.forEachIndexed { index, it ->
                    if (it != PackageManager.PERMISSION_GRANTED) {
                        if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[index]) == true) {
                            deniedPermissions.add(permissions[index])
                        } else {
                            // 거절 했는데 사용자가 더이상 안보기를 선택 한 상황
                            needShowDialog = true

                            mCheckPermissions.add(permissions[index])
                        }
                    }
                }

                if (needShowDialog == true) {
                    val alertDialogBuilder = AlertDialog.Builder(this)
                    alertDialogBuilder.setMessage(getString(R.string.permission_explanation, getAppName()))
                    alertDialogBuilder.setPositiveButton(R.string.set_permission, DialogInterface.OnClickListener { dialog, which ->
                        val intent = Intent()
                        intent.action = Settings.ACTION_APPLICATION_DETAILS_SETTINGS
                        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        intent.data = Uri.fromParts("package", getPackageName(), null)
                        startActivity(intent)

                        dialog.dismiss()
                    })
                    alertDialogBuilder.setNegativeButton(android.R.string.ok, DialogInterface.OnClickListener { dialog, which ->
                        setResultDeniedPermissionIntent(deniedPermissions)
                        finish()
                    })
                    alertDialogBuilder.create().show()

                } else if (deniedPermissions.isEmpty() == false) {
                    setResultDeniedPermissionIntent(deniedPermissions)
                    finish()
                } else { // 권한 모두 수락
                    finish()
                }
            }
        }
    }

    private fun getAppName(): String {
        val stringId = getApplicationInfo().labelRes
        var appName: String

        if (stringId == 0) {
            appName = applicationInfo.nonLocalizedLabel.toString()
        } else {
            appName = getString(stringId)
        }

        return appName
    }

    private fun setResultDeniedPermissionIntent(deniedPermissions: ArrayList<String>) {
        val resultIntent = Intent()
        resultIntent.putExtra(DENIED_SERIALIZABLE_PERMISSION_ARRAYLIST, deniedPermissions)
        setResult(Activity.RESULT_OK, resultIntent)
    }
}