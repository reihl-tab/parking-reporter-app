"""Synthetic, offline VPN-first regression. No tunnel or municipal connection."""
import importlib.util
import ipaddress
import json
from pathlib import Path
import socket
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[1]
spec = importlib.util.spec_from_file_location('trial_l2tp', ROOT / 'trial/l2tp.py')
vpn = importlib.util.module_from_spec(spec)
spec.loader.exec_module(vpn)

class VpnSubmitTest(unittest.TestCase):
    def test_private_destinations_rejected_before_tunnel_start(self):
        with patch.object(vpn, 'run') as run:
            vpn.isolate_private_networks()
        calls = [c.args for c in run.call_args_list]
        self.assertEqual(calls[0], ('iptables', '-N', 'PARK106LAN'))
        self.assertEqual(calls[-1], ('iptables', '-I', 'OUTPUT', '1', '-o', 'ppp42', '-j', 'PARK106LAN'))
        for target in ['10.1.2.3', '172.16.0.1', '192.168.1.1', '169.254.1.1', '100.64.1.1']:
            self.assertTrue(any(ipaddress.ip_address(target) in ipaddress.ip_network(n) for n in vpn.PRIVATE_NETWORKS))
        self.assertTrue(all(c[-1] == 'REJECT' for c in calls[1:-1]))
        with patch.object(vpn, 'run', side_effect=vpn.TunnelError('synthetic')):
            with self.assertRaises(vpn.TunnelError):
                vpn.isolate_private_networks()

    def routes(self, targets, device='ppp42'):
        answers = [(socket.AF_INET, socket.SOCK_STREAM, 6, '', (x, 443)) for x in targets]
        with patch.object(vpn.socket, 'getaddrinfo', return_value=answers), patch.object(vpn, 'run', return_value=json.dumps([{'dev': device}])) as run:
            vpn.verify_municipal_routes()
            return run.call_count

    def test_routes_all_public_targets_must_use_ppp(self):
        self.assertEqual(self.routes(['8.8.8.8', '1.1.1.1']), 2)
        for targets, dev in [([], 'ppp42'), (['192.168.1.1'], 'ppp42'), (['8.8.8.8'], 'eth0')]:
            with self.subTest(targets=targets, dev=dev), self.assertRaises(vpn.TunnelError):
                self.routes(targets, dev)
        with patch.object(vpn.socket, 'getaddrinfo', side_effect=socket.gaierror):
            with self.assertRaises(socket.gaierror):
                vpn.verify_municipal_routes()

    def test_manual_protected_submit_is_vpn_first_single_attempt(self):
        workflow = (ROOT / '.github/workflows/m0-engine-trial.yml').read_text()
        submit = workflow.split('  trial-submit:', 1)[1]
        self.assertIn("github.event_name == 'workflow_dispatch'", submit)
        self.assertIn("github.repository == 'reihlinstinct/parking-reporter-app'", submit)
        self.assertIn("github.ref == 'refs/heads/main'", submit)
        self.assertIn('github.run_attempt == 1', submit)
        self.assertIn('environment: m0-trial-submit', submit)
        self.assertNotIn('continue-on-error', submit)
        self.assertNotIn('up-bounded', submit)
        self.assertEqual(submit.count("--tests 'il.sidewalks.reporter.municipal.EngineLiveTrialTest'"), 1)
        self.assertLess(submit.index('trial_submit_preflight.sh'), submit.index('trial/l2tp.py up'))
        self.assertLess(submit.index('trial/l2tp.py up'), submit.index('Decrypt trial photo'))
        self.assertLess(submit.index('trial/l2tp.py up'), submit.index('Engine trial (single attempt'))
        cleanup = submit.split('- name: Always remove tunnel and trial plaintext')[1]
        self.assertIn('if: always()', cleanup)
        self.assertIn('trial/l2tp.py down', cleanup)
        self.assertIn('rm -f /tmp/trial-original.jpg', cleanup)
        self.assertNotIn('pull_request:', workflow)
        for name in ['SERVER', 'PSK', 'USERNAME', 'PASSWORD']:
            self.assertIn('VPN_L2TP_' + name, submit)

if __name__ == '__main__':
    unittest.main()
