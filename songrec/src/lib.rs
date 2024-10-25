use jni::JNIEnv;
use jni::objects::{JClass, JString};
use jni::sys::jstring;

mod fingerprinting {
    pub mod algorithm;
    pub mod signature_format;
    mod hanning;
    mod decode;
    mod resample;
}

use crate::fingerprinting::algorithm::SignatureGenerator;

#[no_mangle]
pub extern "system" fn Java_io_github_tiefensuche_SongRec_makeSignatureFromFile<'local>(mut env: JNIEnv<'local>,
                                                     class: JClass<'local>,
                                                     input: JString<'local>)
                                                     -> jstring {

    let input: String = env.get_string(&input).expect("Couldn't get java string!").into();
    let signature = SignatureGenerator::make_signature_from_file(&input).expect("error creating signature");
    let samplems = (signature.number_samples as f32 / signature.sample_rate_hz as f32 * 1000.) as u32;
    let output = env.new_string(format!("{},{}", samplems, signature.encode_to_uri().expect("error encoding to uri"))).expect("Couldn't create java string!");
    output.into_raw()
}