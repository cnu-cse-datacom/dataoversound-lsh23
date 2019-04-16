package com.example.sound.devicesound;

import android.media.AudioFormat;
import android.media.AudioRecord;
import android.media.MediaRecorder;
import android.util.Log;


import org.apache.commons.math3.complex.Complex;
import org.apache.commons.math3.transform.*;


import java.io.UnsupportedEncodingException;
import java.util.ArrayList;
import java.util.List;

import static java.lang.Math.abs;

public class Listentone {

    int HANDSHAKE_START_HZ = 4096;
    int HANDSHAKE_END_HZ = 5120 + 1024;

    int START_HZ = 1024;
    int STEP_HZ = 256;
    int BITS = 4;

    int FEC_BYTES = 4;

    private int mAudioSource = MediaRecorder.AudioSource.MIC;
    private int mSampleRate = 44100;
    private int mChannelCount = AudioFormat.CHANNEL_IN_MONO;
    private int mAudioFormat = AudioFormat.ENCODING_PCM_16BIT;
    private float interval = 0.1f;

    private int mBufferSize = AudioRecord.getMinBufferSize(mSampleRate, mChannelCount, mAudioFormat);

    public AudioRecord mAudioRecord = null;
    int audioEncodig;
    boolean startFlag;
    FastFourierTransformer transform;


    public Listentone() {

        transform = new FastFourierTransformer(DftNormalization.STANDARD);
        startFlag = false;
        mAudioRecord = new AudioRecord(mAudioSource, mSampleRate, mChannelCount, mAudioFormat, mBufferSize);
        mAudioRecord.startRecording();

    }

    private int findPowerSize(int round) {
        int size = 1;
        if (round <= 1) {
            return 1;
        } else {
            while (size <= round) {
                size *= 2;
            }
            return size;
        }
        // round 14 -> return 16;
        // round 5 -> return 8;
    }

    private double findFrequency(double[] toTransform) {
        int len = toTransform.length;
        double[] real = new double[len];
        double[] img = new double[len];
        double realNum;
        double imgNum;
        double[] mag = new double[len];

        Complex[] complx = transform.transform(toTransform, TransformType.FORWARD);
        Double[] freq = this.fftfreq(complx.length, 1);

        for (int i = 0; i < complx.length; i++) {
            realNum = complx[i].getReal();
            imgNum = complx[i].getImaginary();
            mag[i] = Math.sqrt((realNum * realNum) + (imgNum * imgNum));
        }

        int indexOfMax = 0;
        double max = Double.MIN_VALUE;
        for (int i = 0; i < complx.length; i++) {
            if (mag[i] > max) {
                max = mag[i];
                indexOfMax = i;
            }
        }
        //decode.py 의 peak_coeff 구하듯이 구함
        double peak_freq = freq[indexOfMax];

        return abs(peak_freq * 44100);

    }

    private Double[] fftfreq(int length, int duration) {

        // f = [0, 1, ...,   n/2-1,     -n/2, ..., -1] / (d*n)   if n is even
        // f = [0, 1, ..., (n-1)/2, -(n-1)/2, ..., -1] / (d*n)   if n is odd

        Double[] sample = new Double[length];
        if (length % 2 == 0) {
            int i = 0;
            for (; i < length / 2; i++) {
                sample[i] = (double) i / (length * duration);
            }
            for (int j = i; j < length ; j++) {
                sample[j] = -(double) i / (length * duration);
                i--;
            }
        } else {
            int i = 0;
            for (; i < (length-1) / 2 ; i++) {
                sample[i] = (double) i / (length * duration);
            }
            for (int j = i; i > length; i--) {
                sample[j] = -(double) i / (length * duration);
            }
        }
        return sample;
    }

    public byte[] extract_packet(List<Double> freqs) {
        byte[] result;
        List<Integer> bit_chunks = new ArrayList<>();
        for (int i = 0; i < freqs.size(); i += 2) // freqs[::2]
            bit_chunks.add((int) Math.round(((freqs.get(i) - START_HZ) / (STEP_HZ))));
        List<Integer> confirmedBit_chunks = new ArrayList<>();
        for (int i = 0; i < bit_chunks.size(); i++) {
            int bit_chunk = bit_chunks.get(i);
            if (0 <= bit_chunk && bit_chunk < Math.pow(2, BITS)) {
                confirmedBit_chunks.add(bit_chunk);
            }

        }
        List<Byte> byteArray = decode_bitchunks(BITS, confirmedBit_chunks);
        result = new byte[byteArray.size()];

        for (int i = 0; i < result.length; i++)
            result[i] = byteArray.get(i);

        return result;
    }

    public List<Byte> decode_bitchunks(int chunk_bits, List<Integer> bit_chunks) {
        // deconde.py 와 똑같이 작성
        List<Byte> out_bytes = new ArrayList<>();

        byte next_read_chunk = 0;
        byte next_read_bit = 0;

        byte _byte = 0;
        byte bits_left = 8;

        while (next_read_chunk < bit_chunks.size()) {
            int can_fill = chunk_bits - next_read_bit;
            int to_fill = Math.min(bits_left, can_fill);
            int offset = chunk_bits - next_read_bit - to_fill;
            _byte <<= to_fill;
            int shifted = bit_chunks.get(next_read_chunk) & (((1 << to_fill) - 1) << offset);
            _byte |= shifted >> offset;
            bits_left -= to_fill;
            next_read_bit += to_fill;
            if (bits_left <= 0) {
                out_bytes.add(_byte);
                _byte = 0;
                bits_left = 8;
            }
            if (next_read_bit >= chunk_bits) {
                next_read_chunk += 1;
                next_read_bit -= chunk_bits;
            }
        }

        return out_bytes;

    }


    boolean match(int freq1, int freq2){
        return abs(freq1 - freq2) < 20;
    }


    public void PreRequest() throws UnsupportedEncodingException {

        int blocksize = findPowerSize((int) (long) Math.round(interval / 2 * mSampleRate));
        short[] buffer = new short[blocksize];
        List<Double> packet = new ArrayList<>();

        while (true) {
            int bufferedReadResult = mAudioRecord.read(buffer, 0, blocksize);

            if (bufferedReadResult < 0)
                continue;

            double[] double_buffer = new double[blocksize];
            for (int i = 0; i < blocksize; i++) {
                double_buffer[i] = (double) buffer[i];
            }
            double dom = findFrequency(double_buffer);

            if (match((int)dom, HANDSHAKE_END_HZ)) {
                byte[] byte_stream = extract_packet(packet);
                Log.d("byte_stream", String.valueOf(byte_stream));
                String converted = new String(byte_stream, "UTF-8");
                Log.d("converted", converted);

                packet = new ArrayList<>();
                startFlag = false;

            } else if (startFlag) {
                packet.add(dom);
            } else if (match((int)dom, HANDSHAKE_START_HZ)) {
                startFlag = true;
            }
        }
    }
}
