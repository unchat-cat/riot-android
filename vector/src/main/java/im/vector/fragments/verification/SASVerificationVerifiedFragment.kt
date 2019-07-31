/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.fragments.verification

import android.arch.lifecycle.ViewModelProviders
import android.os.Bundle
import butterknife.OnClick
import im.vector.R
import im.vector.fragments.VectorBaseFragment

class SASVerificationVerifiedFragment : VectorBaseFragment() {

    override fun getLayoutResId() = R.layout.fragment_sas_verification_verified

    companion object {
        fun newInstance() = SASVerificationVerifiedFragment()
    }

    private lateinit var viewModel: SasVerificationViewModel

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        viewModel = activity?.run {
            ViewModelProviders.of(this).get(SasVerificationViewModel::class.java)
        } ?: throw Exception("Invalid Activity")

    }

    @OnClick(R.id.sas_verification_verified_done_button)
    fun onDone() {
        viewModel.finishSuccess()
    }
}